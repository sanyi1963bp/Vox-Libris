package hu.konyvtar.tts.reader

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import hu.konyvtar.tts.R

/**
 * MOBI / PRC / AZW / AZW3 olvasó (PalmDB konténer).
 * Támogatott: tömörítetlen és PalmDOC (LZ77) szöveg.
 * Nem támogatott: HUFF/CDIC tömörítés és DRM — ezekre érthető hibaüzenetet adunk.
 */
object MobiParser {

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    fun parse(file: File): List<String> {
        val data = file.readBytes()
        if (data.size < 80) throw ExtractException(R.string.err_mobi_broken)

        val type = String(data, 60, 8, Charsets.US_ASCII)
        if (type != "BOOKMOBI" && type != "TEXtREAd") {
            throw ExtractException(R.string.err_mobi_broken)
        }

        val numRecords = u16(data, 76)
        if (numRecords < 2 || 78 + numRecords * 8 > data.size) {
            throw ExtractException(R.string.err_mobi_broken)
        }
        val offsets = IntArray(numRecords)
        for (i in 0 until numRecords) {
            offsets[i] = u32(data, 78 + i * 8).toInt()
        }

        fun record(i: Int): ByteArray {
            val start = offsets[i]
            val end = if (i + 1 < numRecords) offsets[i + 1] else data.size
            if (start < 0 || end > data.size || start >= end) return ByteArray(0)
            return data.copyOfRange(start, end)
        }

        val r0 = record(0)
        if (r0.size < 16) throw ExtractException(R.string.err_mobi_broken)

        val compression = u16(r0, 0)
        val textLength = u32(r0, 4)
        val recordCount = u16(r0, 8)
        val encryption = u16(r0, 12)

        if (encryption != 0) {
            throw ExtractException(R.string.err_drm)
        }

        var charset: Charset = Charset.forName("windows-1252")
        var extraFlags = 0
        if (r0.size >= 24 && String(r0, 16, 4, Charsets.US_ASCII) == "MOBI") {
            val headerLen = u32(r0, 20).toInt()
            val enc = u32(r0, 28).toInt()
            charset = when (enc) {
                65001 -> Charsets.UTF_8
                1252 -> Charset.forName("windows-1252")
                else -> Charsets.UTF_8
            }
            if (headerLen >= 228 && r0.size >= 244) {
                extraFlags = u16(r0, 242)
            }
        }

        when (compression) {
            1 -> {} // tömörítetlen
            2 -> {} // PalmDOC LZ77
            17480 -> throw ExtractException(R.string.err_huff)
            else -> throw ExtractException(R.string.err_mobi_compression, compression)
        }

        val out = ByteArrayOutputStream(maxOf(1024, textLength.toInt()))
        val lastText = minOf(recordCount, numRecords - 1)
        for (i in 1..lastText) {
            var rec = record(i)
            if (rec.isEmpty()) continue
            rec = trimTrailing(rec, extraFlags)
            if (compression == 2) {
                out.write(palmdocDecompress(rec))
            } else {
                out.write(rec)
            }
        }

        var bytes = out.toByteArray()
        if (textLength in 1 until bytes.size) {
            bytes = bytes.copyOfRange(0, textLength.toInt())
        }
        if (bytes.isEmpty()) throw ExtractException(R.string.err_mobi_no_text)

        val html = String(bytes, charset)
        val paras = HtmlText.toParagraphs(html)
        if (paras.isEmpty()) throw ExtractException(R.string.err_mobi_no_text)
        return paras
    }

    /** A rekord végére fűzött extra adatok (index-segédletek) levágása. */
    private fun trimTrailing(rec: ByteArray, flags: Int): ByteArray {
        var end = rec.size
        for (bit in 15 downTo 1) {
            if ((flags shr bit) and 1 == 1) {
                end -= sizeOfTrailingEntry(rec, end)
                if (end < 0) return ByteArray(0)
            }
        }
        if (flags and 1 == 1 && end > 0) {
            val n = (rec[end - 1].toInt() and 0x3) + 1
            end -= n
        }
        if (end <= 0) return ByteArray(0)
        return if (end == rec.size) rec else rec.copyOfRange(0, end)
    }

    private fun sizeOfTrailingEntry(rec: ByteArray, end: Int): Int {
        var num = 0
        val start = maxOf(end - 4, 0)
        for (i in start until end) {
            val v = rec[i].toInt() and 0xFF
            if (v and 0x80 != 0) num = 0
            num = (num shl 7) or (v and 0x7F)
        }
        return num
    }

    /** PalmDOC (LZ77 változat) kitömörítés — egy rekord legfeljebb 4096 bájt szöveg. */
    private fun palmdocDecompress(input: ByteArray): ByteArray {
        val out = ByteArray(8192)
        var pos = 0
        var i = 0
        while (i < input.size && pos < out.size - 8) {
            val c = input[i].toInt() and 0xFF
            i++
            when {
                c == 0 -> {
                    out[pos++] = 0
                }
                c in 1..8 -> {
                    var k = 0
                    while (k < c && i < input.size) {
                        out[pos++] = input[i]
                        i++
                        k++
                    }
                }
                c in 0x09..0x7F -> {
                    out[pos++] = c.toByte()
                }
                c in 0x80..0xBF -> {
                    if (i >= input.size) break
                    val next = input[i].toInt() and 0xFF
                    i++
                    val pair = (c shl 8) or next
                    val distance = (pair shr 3) and 0x7FF
                    val length = (pair and 0x7) + 3
                    if (distance in 1..pos) {
                        var src = pos - distance
                        var k = 0
                        while (k < length && pos < out.size) {
                            out[pos++] = out[src]
                            src++
                            k++
                        }
                    }
                }
                else -> { // 0xC0..0xFF: szóköz + karakter
                    out[pos++] = ' '.code.toByte()
                    out[pos++] = (c xor 0x80).toByte()
                }
            }
        }
        return out.copyOfRange(0, pos)
    }
}
