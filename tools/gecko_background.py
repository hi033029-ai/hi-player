from prepare_gecko_build import replace_once


def augment_background(result):
    result = dict(result)
    key = 'main/compatibility/Patched_GeckoBrowserServices.kt'
    result[key] = replace_once(result[key], '    init {\n        try {', '    init {\n        downloads.onTransferStarted = { cancel -> GeckoTransferForegroundService.acquire(appContext, cancel) }\n        try {', 'foreground download ownership')
    result['AndroidManifest.xml'] = replace_once(result['AndroidManifest.xml'], '</application>', '<service android:name="com.example.browser.gecko.GeckoTransferForegroundService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>', 'foreground service registration')
    key = 'main/engine/GeckoFeatureBridge.kt'
    result[key] = replace_once(result[key], 'it.startsWith("video/") || it.startsWith("audio/")', 'it.startsWith("video/") || it.startsWith("audio/") || it in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")', 'manifest MIME support')
    result[key] = replace_once(result[key], '.put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD)', '.put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD).put("youtube", prefs.youtubeAdSkip && !BuildConfig.PLAY_STORE_BUILD)', 'bundled YouTube preference')
    return result
