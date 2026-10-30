package com.example.lyrics

/** One parser shared by the sync clock and both audio-player layouts. */
data class LyricsText(val raw: String, val timed: List<Pair<Long, String>>, val plain: List<String>) {
    val displayLines: List<String> get() = if (timed.isNotEmpty()) timed.map { it.second } else plain
    companion object {
        private val stamp = Regex("""\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?]""")
        private val offset = Regex("""\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)
        private val metadata = Regex("""^\[(?:ar|ti|al|by|re|ve|length|offset):.*]\s*$""", RegexOption.IGNORE_CASE)
        fun parse(text: String): LyricsText {
            val raw = text.removePrefix("\uFEFF").replace("\r\n", "\n").trim()
            val shift = offset.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val timed = raw.lines().flatMap { line ->
                val marks = stamp.findAll(line).toList()
                if (marks.isEmpty()) emptyList() else {
                    val words = line.substring(marks.last().range.last + 1).trim()
                    marks.map { mark ->
                        val fraction = mark.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                        ((mark.groupValues[1].toLong() * 60_000 + mark.groupValues[2].toLong() * 1000 + fraction + shift).coerceAtLeast(0L)) to words
                    }
                }
            }.sortedBy { it.first }
            val plain = raw.lines().filterNot { metadata.matches(it.trim()) }.map { stamp.replace(it, "").trim() }.filter { it.isNotBlank() }
            return LyricsText(raw, timed, plain)
        }
    }
}
