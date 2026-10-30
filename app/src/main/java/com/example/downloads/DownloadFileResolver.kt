package com.example.downloads

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Resolves a container, not a codec. Never blindly renames arbitrary binary data to MP4. */
internal object DownloadFileResolver {
    data class Result(val fileName: String, val mimeType: String?) {
        val isVideo get() = mimeType?.startsWith("video/") == true
        val isAudio get() = mimeType?.startsWith("audio/") == true
    }
    private data class Type(val extension: String, val mime: String)
    private val types = listOf(
        Type("mp4", "video/mp4"), Type("mkv", "video/x-matroska"), Type("webm", "video/webm"),
        Type("mov", "video/quicktime"), Type("avi", "video/x-msvideo"), Type("flv", "video/x-flv"),
        Type("ts", "video/mp2t"), Type("mpeg", "video/mpeg"), Type("3gp", "video/3gpp"),
        Type("webm", "audio/webm"), Type("mka", "audio/x-matroska"), Type("m4a", "audio/mp4"), Type("mp3", "audio/mpeg"), Type("wav", "audio/wav"),
        Type("flac", "audio/flac"), Type("ogg", "audio/ogg"), Type("aac", "audio/aac"),
        Type("avif", "image/avif"), Type("heic", "image/heic"), Type("html", "text/html"),
    )
    private val genericExtensions = setOf("", "bin", "dat", "download", "tmp", "part")
    fun isGenericMime(mime: String?): Boolean = normalizeMime(mime) in setOf(null, "application/octet-stream", "binary/octet-stream", "application/download", "application/force-download", "application/x-download")
    fun normalizeMime(value: String?): String? = when (val mime = value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }) {
        "video/x-mp4", "application/mp4" -> "video/mp4"
        "video/matroska" -> "video/x-matroska"
        "video/avi", "video/msvideo" -> "video/x-msvideo"
        "audio/mp3", "audio/x-mp3", "audio/mpeg3", "audio/x-mpeg3", "application/mp3" -> "audio/mpeg"
        "audio/x-wav" -> "audio/wav"
        else -> mime
    }
    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    private fun typeForExtension(ext: String): Type? = when (ext) {
        "m4v" -> types.first { it.extension == "mp4" }
        "mpg", "vob" -> types.first { it.extension == "mpeg" }
        "m2ts", "mts" -> types.first { it.extension == "ts" }
        "3gpp" -> types.first { it.extension == "3gp" }
        else -> types.firstOrNull { it.extension == ext }
    }
    /** Cheap enough for queue change detection; never inspects URLs or allocates sanitized names. */
    fun isAudioFile(fileName: String, mimeType: String?): Boolean {
        val mime = normalizeMime(mimeType)
        return if (isGenericMime(mime)) typeForExtension(extension(fileName))?.mime?.startsWith("audio/") == true
        else mime?.startsWith("audio/") == true
    }
    fun resolve(
        fileName: String, sourceUrl: String, mimeType: String?, contentDisposition: String? = null,
        prefix: ByteArray = byteArrayOf(), detectedMime: String? = null,
    ): Result {
        var name = dispositionName(contentDisposition) ?: fileName
        if (extension(name) in genericExtensions) {
            urlName(sourceUrl)?.takeIf { typeForExtension(extension(it)) != null }?.let { name = it }
        }
        name = safeName(name)
        val sniffed = sniff(prefix)
        val retrieved = types.firstOrNull { it.mime == normalizeMime(detectedMime) }
        val declared = normalizeMime(mimeType)
        val selected = when {
            // Metadata can disambiguate an audio-only MP4/WebM from a video container.
            retrieved != null && sniffed?.extension in setOf("mp4", "m4a", "webm", "mkv") -> retrieved
            sniffed != null -> sniffed
            retrieved != null -> retrieved
            !isGenericMime(declared) -> types.firstOrNull { it.mime == declared }
            else -> typeForExtension(extension(name))
        }
        val resolvedMime = selected?.mime ?: declared
        val ext = extension(name)
        if (selected != null && (ext in genericExtensions || sniffed != null || retrieved != null)) {
            if (ext != selected.extension && typeForExtension(ext)?.mime != selected.mime) {
                val stem = if (ext.isEmpty()) name else name.substringBeforeLast('.')
                name = if (extension(stem) == selected.extension) stem else "$stem.${selected.extension}"
            }
        }
        return Result(safeName(name), resolvedMime)
    }
    internal fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifBlank { "download" }
        if (cleaned.length <= 120) return cleaned
        val ext = extension(cleaned).takeIf { it.length in 1..8 }
        return if (ext != null) cleaned.substringBeforeLast('.').take(119 - ext.length) + ".$ext" else cleaned.take(120)
    }
    internal fun dispositionName(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val extended = Regex("(?:^|;)\\s*filename\\*\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))", RegexOption.IGNORE_CASE).find(value)
        val encoded = extended?.let { it.groupValues[1].ifBlank { it.groupValues[2].trim() } }
        if (encoded != null) {
            val pieces = encoded.split("'", limit = 3)
            if (pieces.size == 3 && pieces[0].lowercase(Locale.ROOT) in setOf("utf-8", "iso-8859-1")) {
                runCatching { URLDecoder.decode(pieces[2].replace("+", "%2B"), pieces[0]) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        val plain = Regex("(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))", RegexOption.IGNORE_CASE).find(value)
        return plain?.let { it.groupValues[1].ifBlank { it.groupValues[2].trim() } }?.takeIf { it.isNotBlank() }
    }
    private fun urlName(value: String): String? = runCatching {
        val uri = URI(value)
        val fromQuery = uri.rawQuery?.split('&')?.mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2 && parts[0].lowercase(Locale.ROOT) in setOf("filename", "file", "name", "download")) URLDecoder.decode(parts[1], "UTF-8") else null
        }?.firstOrNull { typeForExtension(extension(it)) != null }
        fromQuery ?: uri.rawPath?.substringAfterLast('/')?.let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
    }.getOrNull()
    private fun sniff(bytes: ByteArray): Type? {
        fun text(offset: Int, length: Int) = if (offset + length <= bytes.size) String(bytes, offset, length, Charsets.ISO_8859_1) else ""
        fun type(ext: String) = types.first { it.extension == ext }
        if (bytes.size >= 12 && text(4, 4) == "ftyp") {
            val brand = text(8, 4)
            return type(when {
                brand in setOf("avif", "avis") -> "avif"
                brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") -> "heic"
                brand.startsWith("M4A") || brand.startsWith("M4B") || brand.startsWith("M4P") -> "m4a"
                brand.startsWith("3g") -> "3gp"
                brand == "qt  " -> "mov"
                brand in setOf("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "M4V ", "MSNV", "F4V ") -> "mp4"
                else -> return null
            })
        }
        if (bytes.size >= 4 && bytes.take(4).map { it.toInt() and 255 } == listOf(0x1a, 0x45, 0xdf, 0xa3)) {
            val header = text(0, minOf(bytes.size, 65536))
            if (header.contains("webm")) return type("webm")
            if (header.contains("matroska")) return type("mkv")
        }
        if (text(0, 4) == "RIFF" && text(8, 4) == "AVI ") return type("avi")
        if (text(0, 4) == "RIFF" && text(8, 4) == "WAVE") return type("wav")
        if (text(0, 3) == "FLV") return type("flv")
        if (text(0, 4) == "fLaC") return type("flac")
        if (text(0, 3) == "ID3") return type("mp3")
        if (bytes.size > 376 && listOf(0, 188, 376).all { bytes[it] == 0x47.toByte() }) return type("ts")
        if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte() && (bytes[3].toInt() and 255) == 0xba) return type("mpeg")
        val start = text(0, minOf(bytes.size, 200)).trimStart().lowercase(Locale.ROOT)
        if (start.startsWith("<!doctype html") || start.startsWith("<html")) return type("html")
        return null
    }
}
