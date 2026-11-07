#!/usr/bin/env python3
"""Materialize complete source roots for AGP 9, which no longer supports file exclusions."""
import hashlib
from pathlib import Path
from prepare_gecko_build import PREFIX, prepare
from gecko_features import augment

MAIN_PREFIXES = ('app/src/main/java/', 'app/src/main/kotlin/')
TEST_PREFIXES = ('app/src/test/java/', 'app/src/test/kotlin/')

def materialize(files, generated):
    result = {name: content for name, content in generated.items() if not name.endswith(('.kt', '.java'))}
    for name, content in generated.items():
        if name.endswith(('.kt', '.java')):
            kind, filename = name.split('/', 1)
            result[kind + '/compatibility/' + filename] = content
    for path, content in files.items():
        if not path.endswith(('.kt', '.java')):
            continue
        matched = next((prefix for prefix in MAIN_PREFIXES if path.startswith(prefix)), None)
        if matched:
            filename = Path(path).name
            if path == 'app/src/main/java/com/example/ui/screens/HiBrowserScreen.kt' or 'main/' + filename in generated:
                continue
            result['main/application/' + path[len(matched):]] = content
            continue
        matched = next((prefix for prefix in TEST_PREFIXES if path.startswith(prefix)), None)
        if matched:
            filename = Path(path).name
            if filename == 'SoftwareDecoderRegistrationTest.kt' or 'test/' + filename in generated:
                continue
            result['test/application/' + path[len(matched):]] = content
            continue
        if path.startswith(PREFIX):
            relative = path[len(PREFIX):]
            if '/' not in relative:
                if relative == 'LegacyGav1RenderersFactory.kt' or 'main/Patched_' + relative in generated:
                    continue
                result['main/engine/' + relative] = content
            elif relative.startswith('integration/'):
                result['main/' + relative] = content
            elif relative.startswith(('tests/', 'integration-tests/')):
                if Path(relative).name != 'LegacyGav1RegistrationTest.kt':
                    result['test/engine/' + relative] = content
    return result

def main():
    root = Path(__file__).resolve().parents[1]
    needed = [
        'app/build.gradle.kts', 'build.gradle.kts', 'app/src/main/AndroidManifest.xml',
        'gradle/gecko-integration.gradle.kts',
        *[PREFIX + 'patches/' + name for name in ('shared-address-input.patch', 'media3-111-player-candidate.patch', 'host-lifecycle.patch')],
    ]
    files = {name: (root / name).read_text() for name in needed}
    roots = [*MAIN_PREFIXES, *TEST_PREFIXES, PREFIX]
    for name in roots:
        directory = root / name
        if not directory.is_dir():
            continue
        paths = directory.glob('*.kt') if name == PREFIX else directory.rglob('*')
        for path in paths:
            if path.is_file() and path.suffix in ('.kt', '.java'):
                files[path.relative_to(root).as_posix()] = path.read_text()
    for name in ('tests', 'integration', 'integration-tests'):
        for path in (root / PREFIX / name).glob('*.kt'):
            files[path.relative_to(root).as_posix()] = path.read_text()
    generated = augment(materialize(files, prepare(files)))
    for script in ('orbit_adblock.js', 'orbit_page_skipper.js'):
        generated['assets/hi-gecko/' + script] = (root / 'app/src/main/assets' / script).read_text()
    output = root / 'build/generated/gecko'
    output.mkdir(parents=True, exist_ok=True)
    for path in output.rglob('*'):
        if path.is_file() and path.relative_to(output).as_posix() not in generated:
            path.unlink()
    for name, content in generated.items():
        path = output / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists() or path.read_text() != content:
            path.write_text(content)
    fingerprint = hashlib.sha256(''.join(name + '\0' + generated[name] for name in sorted(generated)).encode()).hexdigest()
    print('Prepared complete Gecko source roots ' + fingerprint)

if __name__ == '__main__':
    main()
