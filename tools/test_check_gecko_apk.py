import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools.check_gecko_apk import inspect_apk


def fake_elf(alignments, bits=64):
    header_len, entry_len = (64, 56) if bits == 64 else (52, 32)
    out = bytearray(header_len + entry_len * len(alignments))
    out[:6] = b'\x7fELF' + bytes([2 if bits == 64 else 1, 1])
    if bits == 64:
        struct.pack_into('<Q', out, 32, header_len)
        struct.pack_into('<HH', out, 54, entry_len, len(alignments))
    else:
        struct.pack_into('<I', out, 28, header_len)
        struct.pack_into('<HH', out, 42, entry_len, len(alignments))
    for i, value in enumerate(alignments):
        offset = header_len + i * entry_len
        struct.pack_into('<I', out, offset, 1)
        struct.pack_into('<Q' if bits == 64 else '<I', out, offset + (48 if bits == 64 else 28), value)
    return out


class ApkCheckTest(unittest.TestCase):
    def make_apk(self, path, libs, assets=True):
        with zipfile.ZipFile(path, 'w') as apk:
            for name, content in libs.items():
                apk.writestr(name, content)
            if assets:
                apk.writestr('assets/hi-gecko/manifest.json', '{}')
                apk.writestr('assets/gecko_component_licenses.txt', 'licenses')

    def test_reports_every_segment_and_all_failing_libraries(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / 'test.apk'
            self.make_apk(apk, {'lib/x86_64/liba.so': fake_elf([4096, 16384]),
                                'lib/x86_64/libb.so': fake_elf([4096, 4096]),
                                'lib/x86_64/libok.so': fake_elf([16384, 65536])})
            report = inspect_apk(apk, 'x86_64')
            self.assertEqual(report['native_load_segments']['lib/x86_64/liba.so'], [4096, 16384])
            self.assertEqual(report['native_load_segments']['lib/x86_64/libok.so'], [16384, 65536])
            self.assertEqual(len(report['errors']), 2)
            self.assertTrue(any('liba.so' in e for e in report['errors']))
            self.assertTrue(any('libb.so' in e for e in report['errors']))

    def test_32_bit_elf_and_required_checks_are_not_skipped(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / 'test.apk'
            self.make_apk(apk, {'lib/armeabi-v7a/libgav1JNI.so': fake_elf([4096], bits=32)}, assets=False)
            report = inspect_apk(apk, 'arm64-v8a')
            self.assertEqual(report['native_load_segments']['lib/armeabi-v7a/libgav1JNI.so'], [4096])
            for text in ('Expected only arm64-v8a', 'Unsafe legacy JNI', '16 KB', 'Local extension', 'Offline source notices'):
                self.assertTrue(any(text in e for e in report['errors']), text)

    def test_cli_writes_full_report_even_when_validation_fails(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / 'test.apk'
            self.make_apk(apk, {'lib/x86_64/liba.so': fake_elf([4096]),
                                'lib/x86_64/libb.so': fake_elf([4096, 16384])})
            script = Path(__file__).with_name('check_gecko_apk.py').resolve()
            env = dict(os.environ, HI_PLAYER_APK_PATH=str(apk), HI_PLAYER_EXPECTED_APK_ABI='x86_64')
            result = subprocess.run([sys.executable, str(script)], cwd=d, env=env, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            import json
            report = json.loads((Path(d) / 'build/gecko-apk-review.json').read_text())
            self.assertEqual(len(report['errors']), 2)
            self.assertEqual(len(report['native_load_segments']), 2)
            self.assertIn('liba.so', result.stderr)
            self.assertIn('libb.so', result.stderr)

    def test_size_cap_and_malformed_elf_reported(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / 'test.apk'
            self.make_apk(apk, {'lib/arm64-v8a/libbad.so': b'bad'})
            with patch('tools.check_gecko_apk.MAX_ARM64_APK_BYTES', 1):
                report = inspect_apk(apk, 'arm64-v8a')
            self.assertTrue(any('size budget' in e for e in report['errors']))
            self.assertTrue(any('Cannot inspect native library' in e for e in report['errors']))


if __name__ == '__main__':
    unittest.main()
