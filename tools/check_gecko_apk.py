import json, os, struct, zipfile
from pathlib import Path

apk = next(Path('app/build/outputs/apk/debug').glob('*.apk'))
report = {'apk_bytes': apk.stat().st_size, 'apk_mib': round(apk.stat().st_size / 1048576, 2), 'abis': [], 'native_load_alignment': {}}
expected_abi = os.environ.get('HI_PLAYER_EXPECTED_APK_ABI', 'x86_64')
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    report['abis'] = sorted({name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')})
    assert expected_abi in report['abis'], f'Expected APK ABI missing: {expected_abi}'
    if expected_abi == 'arm64-v8a':
        assert report['abis'] == ['arm64-v8a'], f'APK unexpectedly contains extra ABIs: {report["abis"]}'
        assert report['apk_bytes'] <= 250 * 1024 * 1024, 'Arm64-only APK exceeds 250 MiB size budget'
    assert not any(name.endswith('/libgav1JNI.so') for name in names), 'Unsafe legacy JNI bridge packaged'
    for name in names:
        if not name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) or not name.endswith('.so'): continue
        with archive.open(name) as stream:
            header = stream.read(64)
            if header[:5] != bytes((127, 69, 76, 70, 2)): continue
            offset = struct.unpack_from('<Q', header, 32)[0]
            size, count = struct.unpack_from('<HH', header, 54)
            data = header + stream.read(offset + size * count - 64)
            alignments = [struct.unpack_from('<Q', data, offset + index * size + 48)[0] for index in range(count) if struct.unpack_from('<I', data, offset + index * size)[0] == 1]
            report['native_load_alignment'][name] = min(alignments)
            assert min(alignments) >= 16384, 'Native library lacks 16 KB page alignment: ' + name
    assert 'assets/hi-gecko/manifest.json' in names, 'Local extension not packaged'
    assert 'assets/gecko_component_licenses.txt' in names, 'Offline source notices missing'
Path('build').mkdir(exist_ok=True)
Path('build/gecko-apk-review.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
