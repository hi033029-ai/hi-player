from prepare_gecko_build import replace_once


def augment_background(result):
    result = dict(result)
    key = 'main/compatibility/Patched_GeckoBrowserServices.kt'
    result[key] = replace_once(result[key], '    init {\n        try {', '    init {\n        downloads.onTransferStarted = { cancel -> GeckoTransferForegroundService.acquire(appContext, cancel) }\n        try {', 'foreground download ownership')
    result['AndroidManifest.xml'] = replace_once(result['AndroidManifest.xml'], '</application>', '<service android:name="com.example.browser.gecko.GeckoTransferForegroundService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>', 'foreground service registration')
    key = 'main/engine/GeckoFeatureBridge.kt'
    result[key] = replace_once(result[key], 'it.startsWith("video/") || it.startsWith("audio/")', 'it.startsWith("video/") || it.startsWith("audio/") || it in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")', 'manifest MIME support')
    result[key] = replace_once(result[key], '.put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD)', '.put("pageSkipper", prefs.pageSkipper && !BuildConfig.PLAY_STORE_BUILD).put("youtube", prefs.youtubeAdSkip && !BuildConfig.PLAY_STORE_BUILD)', 'bundled YouTube preference')
    result[key] = replace_once(result[key], '"reader" -> {\n                    if (sender.url.substringBefore(\'#\') != client.page().substringBefore(\'#\')) return null', '"reader" -> {\n                    if (message.optString("page").substringBefore(\'#\') != client.page().substringBefore(\'#\')) return null', 'current-document reader result')
    key = 'main/engine/GeckoPageActions.kt'
    result[key] = replace_once(result[key], '    var reader by remember', '    var notices by remember { mutableStateOf(false) }\n    var reader by remember', 'notices UI state')
    result[key] = replace_once(result[key], '            Spacer(Modifier.height(16.dp))', '            TextButton(onClick = { onDismiss(); notices = true }) { Text("Open source notices") }\n            TextButton(onClick = { onDismiss(); services.coordinator.load("about:license") }) { Text("Gecko third-party notices") }\n            Spacer(Modifier.height(16.dp))', 'offline notices controls')
    result[key] = replace_once(result[key], '    notice?.let { message ->', '    if (notices) GeckoNoticesDialog { notices = false }\n    notice?.let { message ->', 'offline notices dialog')
    key = 'main/engine/GeckoNavigationPolicy.kt'
    result[key] = replace_once(result[key], '        val scheme = runCatching', '        if (url == "about:license") return GeckoNavigationDecision.Allow\n        val scheme = runCatching', 'public Gecko license page')
    return result
