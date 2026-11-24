package com.example.util

import com.example.model.PendingDownloadFormat

private val releaseTag = Regex(
    """(?i)(?<![\p{L}\p{N}])(?:[48]k|uhd|imax|(?:2160|1080|720|480)p|blu[ -]?ray|web[ -]?(?:dl|rip)|bd(?:rip|remux)|remux|hdr(?:10\+?)?|dolby[ -]vision|hevc|[hx][ -]?26[45]|(?:8|10|12)[ -]?bit|dts(?:[ -]hd)?|atmos|aac|ddp?\d)(?![\p{L}\p{N}])"""
)
private val bracketedYear = Regex("""\s*[\[(](?:19|20)\d{2}[\])]\s*$""")

internal fun subtitleSearchTitle(displayName: String): String {
    if (displayName.contains("://") || displayName.any { it in "/\\" }) return ""
    val name = PendingDownloadFormat.originalName(displayName.trim())
    val stem = if (name.substringAfterLast('.').lowercase() in PendingDownloadFormat.videoExtensions)
        name.substringBeforeLast('.') else name
    val words = stem.replace('.', ' ').replace('_', ' ').trim()
    val tag = releaseTag.find(words)
    val title = if (tag != null && tag.range.first > 0) words.take(tag.range.first) else words
    return title.trimEnd(' ', '-', '[', '(').replace(bracketedYear, "").trim().replace(Regex("""\s+"""), " ")
}
