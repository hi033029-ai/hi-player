"""Validate APK packaging, recording every native ELF load segment before failure."""
import json
import os
import struct
import zipfile
from pathlib import Path

MIN_ALIGNMENT = 16 * 1024
MAX_ARM64_APK_BYTES = 250 * 1024 * 1024


def elf_load_alignments(archive, info):
    """Return PT_LOAD p_align values for a 32- or 64-bit ELF member."""
    with archive.open(info) as stream:
        header = stream.read(64)
        if len(header) < 52 or header[:4] != b'\x7fELF':
            raise ValueError('not a valid ELF header')
        elf_class, data_encoding = header[4], header[5]
        if data_encoding not in (1, 2):
            raise ValueError('unknown ELF byte order')
        endian = '<' if data_encoding == 1 else '>'
        if elf_class == 1:
            phoff = struct.unpack_from(endian + 'I', header, 28)[0]
            phentsize, phnum = struct.unpack_from(endian + 'HH', header, 42)
            required_size, align_offset, align_type, header_size = 32, 28, 'I', 52
        elif elf_class == 2 and len(header) >= 64:
            phoff = struct.unpack_from(endian + 'Q', header, 32)[0]
            phentsize, phnum = struct.unpack_from(endian + 'HH', header, 54)
            required_size, align_offset, align_type, header_size = 56, 48, 'Q', 64
        else:
            raise ValueError('unknown or truncated ELF class')
        if not phnum or phnum == 0xffff or phentsize < required_size:
            raise ValueError('invalid or unsupported program header table')
        end = phoff + phentsize * phnum
        if end > info.file_size or phoff < header_size:
            raise ValueError('program header table outside ELF')
        stream.seek(phoff)
        table = stream.read(phentsize * phnum)
        if len(table) != phentsize * phnum:
            raise ValueError('truncated program header table')
        loads = [struct.unpack_from(endian + align_type, table, i * phentsize + align_offset)[0]
                 for i in range(phnum)
                 if struct.unpack_from(endian + 'I', table, i * phentsize)[0] == 1]
        if not loads:
            raise ValueError('ELF has no PT_LOAD segments')
        return loads


def inspect_apk(apk, expected_abi):
    size = apk.stat().st_size
    report = {'apk_file': str(apk), 'apk_bytes': size, 'apk_mib': round(size / 1048576, 2),
              'abis': [], 'native_load_alignment': {}, 'native_load_segments': {}, 'errors': []}
    errors = report['errors']
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        report['abis'] = sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so') and len(n.split('/')) >= 3})
        if report['abis'] != [expected_abi]:
            errors.append(f'Expected only {expected_abi}, found {report["abis"]}')
        if expected_abi == 'arm64-v8a' and size > MAX_ARM64_APK_BYTES:
            errors.append('Arm64-only APK exceeds 250 MiB size budget')
        if any(n.endswith('/libgav1JNI.so') for n in names):
            errors.append('Unsafe legacy JNI bridge packaged')
        for info in archive.infolist():
            name = info.filename
            if not name.startswith('lib/') or not name.endswith('.so'):
                continue
            try:
                alignments = elf_load_alignments(archive, info)
                report['native_load_segments'][name] = alignments
                report['native_load_alignment'][name] = min(alignments)
                if any(value < MIN_ALIGNMENT for value in alignments):
                    errors.append(f'Native library lacks 16 KB page alignment: {name} (PT_LOAD p_align={alignments})')
            except (ValueError, struct.error, EOFError) as exc:
                errors.append(f'Cannot inspect native library: {name} ({exc})')
        if 'assets/hi-gecko/manifest.json' not in names:
            errors.append('Local extension not packaged')
        if 'assets/gecko_component_licenses.txt' not in names:
            errors.append('Offline source notices missing')
    return report


def main():
    apk_path = os.environ.get('HI_PLAYER_APK_PATH')
    apk = Path(apk_path) if apk_path else next(Path('app/build/outputs/apk/debug').glob('*.apk'))
    if not apk.is_file():
        raise SystemExit(f'APK not found: {apk}')
    report = inspect_apk(apk, os.environ.get('HI_PLAYER_EXPECTED_APK_ABI', 'x86_64'))
    Path('build').mkdir(exist_ok=True)
    Path('build/gecko-apk-review.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    if report['errors']:
        raise SystemExit('APK validation failed:\n' + '\n'.join(report['errors']))


if __name__ == '__main__':
    main()
