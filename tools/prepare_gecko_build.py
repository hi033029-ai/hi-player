#!/usr/bin/env python3
"""Prepare an opt-in Gecko build without modifying the supported WebView build.

Generated sources are ordinary Kotlin/manifest inputs. All transformations are
context-checked and fail closed if an upstream source or patch has changed.
"""
import re
from pathlib import Path

PREFIX = 'experiments/gecko-browser/'

def apply_patch(files, patch):
    result = dict(files)
    for section in re.split(r'(?m)^diff --git ', patch)[1:]:
        target = re.search(r'(?m)^\+\+\+ b/(.+)$', section).group(1)
        original = result.get(target, '').splitlines(keepends=True)
        output, cursor = [], 0
        hunks = list(re.finditer(r'(?m)^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@[^\n]*\n', section))
        for index, hunk in enumerate(hunks):
            start = max(0, int(hunk.group(1)) - 1)
            if start < cursor or start > len(original):
                raise ValueError('Invalid patch position: ' + target)
            output.extend(original[cursor:start])
            cursor = start
            body = section[hunk.end():hunks[index + 1].start() if index + 1 < len(hunks) else len(section)]
            for line in body.splitlines(keepends=True):
                if line.startswith((' ', '-')):
                    if cursor >= len(original) or original[cursor] != line[1:]:
                        raise ValueError('Patch context changed: ' + target)
                    cursor += 1
                if line.startswith((' ', '+')):
                    output.append(line[1:])
                elif line.startswith('\\ No newline'):
                    raise ValueError('Non-terminated patch input: ' + target)
        output.extend(original[cursor:])
        result[target] = ''.join(output)
    return result

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise ValueError('Expected one ' + label)
    return text.replace(old, new, 1)

def prepare(files):
    patched = dict(files)
    for patch in ('shared-address-input.patch', 'media3-111-player-candidate.patch', 'host-lifecycle.patch'):
        patched = apply_patch(patched, files[PREFIX + 'patches/' + patch])
    generated = {}
    for path, content in patched.items():
        if path in files and content == files[path]:
            continue
        category = 'test' if '/src/test/' in path else 'main'
        generated[category + '/' + ('Patched_' if path.startswith(PREFIX) else '') + Path(path).name] = content
    legacy = 'app/src/main/java/com/example/ui/screens/HiBrowserScreen.kt'
    generated['main/LegacyHiBrowserScreen.kt'] = replace_once(
        patched[legacy], 'fun HiBrowserScreen(', 'fun WebViewBrowserScreen(', 'legacy browser entry point')
    generated.pop('main/HiBrowserScreen.kt', None)
    # Bit 6 changed from editable tracks to auxiliary tracks in newer Media3.
    # Never carry the old inverse mask or turn on the new auxiliary-track flag.
    player = generated['main/OptimizedPlayerBuilder.kt']
    player = replace_once(player,
        'Mp4Extractor.FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES or\n                    (1 shl 6) // Media3 1.5 editable video tracks; 1.11 auxiliary tracks.',
        'Mp4Extractor.FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES', 'MP4 compatibility flags')
    generated['main/OptimizedPlayerBuilder.kt'] = player
    test = generated['test/OptimizedPlayerBuilderFlagsTest.kt']
    generated['test/OptimizedPlayerBuilderFlagsTest.kt'] = test.replace('assertEquals(0x7d,', 'assertEquals(0x3d,')
    decoder_test = files['app/src/test/java/com/example/player/SoftwareDecoderRegistrationTest.kt']
    generated['test/SoftwareDecoderRegistrationTest_Gecko.kt'] = decoder_test.replace('Libgav1', 'Libdav1d')
    manifest = files['app/src/main/AndroidManifest.xml']
    marker = '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />'
    additions = """    <!-- Gecko is API 26+. The routed entry point keeps API 24-25 on WebView. -->
    <uses-sdk tools:overrideLibrary="org.mozilla.geckoview,org.mozilla.geckoview_exoplayer2" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />
"""
    generated['AndroidManifest.xml'] = replace_once(manifest, marker, additions + marker, 'manifest permission insertion')
    app = files['app/build.gradle.kts']
    app = replace_once(app, 'release(36) { minorApiLevel = 1 }', 'release(37) { minorApiLevel = 2 }', 'compile SDK')
    if app.count('JavaVersion.VERSION_11') != 2:
        raise ValueError('Unexpected Java toolchain configuration')
    app = app.replace('JavaVersion.VERSION_11', 'JavaVersion.VERSION_17')
    for old, new in [('media3-ffmpeg-decoder-debug.aar', 'media3-ffmpeg-1.11.1.aar'),
                     ('media3-av1-decoder-release.aar', 'media3-av1-1.11.1.aar')]:
        app = replace_once(app, 'implementation(files("libs/' + old + '"))',
            'implementation(files("$rootDir/build/gecko-decoders/' + new + '").builtBy("prepareGeckoDecoders"))', old)
    app += '\n// Opt-in engine integration; the normal build file remains unchanged.\n'
    app += 'apply(from = rootProject.file("gradle/gecko-integration.gradle.kts"))\n'
    generated['app.gradle.kts'] = app
    generated['root.gradle.kts'] = """buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.13")
    }
}
""" + files['build.gradle.kts']
    return generated

def main():
    root = Path(__file__).resolve().parents[1]
    needed = [
        'app/build.gradle.kts', 'build.gradle.kts', 'app/src/main/AndroidManifest.xml',
        'app/src/main/java/com/example/ui/screens/HiBrowserScreen.kt',
        'app/src/main/java/com/example/playback/OptimizedPlayerBuilder.kt',
        'app/src/main/java/com/example/player/SharpnessReductionEffect.kt',
        'app/src/test/java/com/example/player/SoftwareDecoderRegistrationTest.kt',
        PREFIX + 'patches/shared-address-input.patch', PREFIX + 'patches/media3-111-player-candidate.patch', PREFIX + 'patches/host-lifecycle.patch',
        *[PREFIX + name for name in ('GeckoFilePickerLauncher.kt', 'GeckoRuntimePermissionLauncher.kt', 'GeckoBrowserServices.kt', 'GeckoTabController.kt', 'GeckoManagedTab.kt', 'GeckoTabEngine.kt', 'GeckoBrowserChrome.kt')],
    ]
    generated = prepare({name: (root / name).read_text() for name in needed})
    output = root / 'build/generated/gecko'
    output.mkdir(parents=True, exist_ok=True)
    # This directory is exclusively generated; stale Kotlin sources must not survive.
    expected = set(generated)
    for path in output.rglob('*'):
        if path.is_file() and path.relative_to(output).as_posix() not in expected:
            path.unlink()
    for name, content in generated.items():
        path = output / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists() or path.read_text() != content:
            path.write_text(content)
    print('Prepared opt-in Gecko inputs; minSdk, targetSdk and app version are unchanged.')

if __name__ == '__main__':
    main()
