from prepare_gecko_build import replace_once


def augment_background(result):
    result = dict(result)
    key = 'main/compatibility/Patched_GeckoBrowserServices.kt'
    result[key] = replace_once(result[key], '    init {\n        try {', '    init {\n        downloads.onTransferStarted = { cancel -> GeckoTransferForegroundService.acquire(appContext, cancel) }\n        try {', 'foreground download ownership')
    result['AndroidManifest.xml'] = replace_once(result['AndroidManifest.xml'], '</application>', '<service android:name="com.example.browser.gecko.GeckoTransferForegroundService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>', 'foreground service registration')
    return result
