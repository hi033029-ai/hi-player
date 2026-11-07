import hashlib
from pathlib import Path
from urllib.request import urlopen

COMPONENTS = [
 ('Media3 1.11.1', 'https://raw.githubusercontent.com/androidx/media/8c6678b657ede1e7883fc164ef73ed483c7796c3/LICENSE', 'd645695673349e3947e8e5ae42332d0ac3164cd7'),
 ('dav1d', 'https://raw.githubusercontent.com/videolan/dav1d/42b2b24fb8819f1ed3643aa9cf2a62f03868e3aa/COPYING', '875b138ecf6f2b2b8e402c51ea541d7740246504'),
 ('FFmpeg LGPL 2.1', 'https://raw.githubusercontent.com/FFmpeg/FFmpeg/573f2b81ebd97da81def2ba762e81b0328547004/COPYING.LGPLv2.1', '40924c2a6da76a2b0c639f6fe7ef0b2d095a6adb'),
 ('cpu_features', 'https://raw.githubusercontent.com/google/cpu_features/ba4bffa86cbb5456bdb34426ad22b9551278e2c0/LICENSE', 'a7043c6fdedacc991803384333201058ea1a8865'),
 ('Gecko license notice', 'https://raw.githubusercontent.com/mozilla/gecko-dev/master/LICENSE', '373395d73e6664eb0dc595c0226ca74d3906b671')]


def notices(root):
    cache = Path(root) / 'build/gecko-license-cache'
    cache.mkdir(parents=True, exist_ok=True)
    output = ['Hi Player Gecko component notices\n\nGeckoView 159.0.20261102144711: MPL 2.0 and bundled third-party terms. Complete Gecko notices are included in Gecko and available through Browser options > Gecko third-party notices.\nCorresponding Gecko source: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/159.0.20261102144711/geckoview-159.0.20261102144711-sources.jar\nNative Media3/dav1d/cpu_features/FFmpeg source revisions and reproducible build commands: experiments/gecko-browser/aligned-decoders/build.sh in the Hi Player source repository. Native libraries are dynamically loaded; rebuilding/replacing LGPL components is not prohibited.\n']
    for name, url, expected in COMPONENTS:
        path = cache / expected
        data = path.read_bytes() if path.exists() else urlopen(url, timeout=30).read()
        actual = hashlib.sha1(b'blob ' + str(len(data)).encode() + bytes((0,)) + data).hexdigest()
        if actual != expected: raise ValueError('License source changed: ' + name)
        if not path.exists(): path.write_bytes(data)
        output.append('\n\n' + name + '\nSource/license: ' + url + '\n\n' + data.decode('utf-8'))
    return '\n'.join(output)
