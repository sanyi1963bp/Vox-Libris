package hu.konyvtar.tts.reader

import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer

/** Sima szövegfájl beolvasása kódolás-felismeréssel (UTF-8/UTF-16/Windows-1250). */
object TxtParser {

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // Szigorú UTF-8 próba; ha nem érvényes, magyar szövegnél a Windows-1250 a tipikus
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1250"))
        }
    }

    fun parse(bytes: ByteArray): List<String> {
        val text = decode(bytes)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("​", "")
            .replace("﻿", "")
        val lines = text.split('\n')
        val blankCount = lines.count { it.isBlank() }

        return if (blankCount >= lines.size / 50 && blankCount >= 3) {
            // Üres sorokkal tagolt szöveg: üres sor = bekezdéshatár, a sortörések szóközzé olvadnak
            val out = ArrayList<String>()
            val sb = StringBuilder()
            for (line in lines) {
                if (line.isBlank()) {
                    if (sb.isNotEmpty()) {
                        out.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                } else {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(line.trim())
                }
            }
            if (sb.isNotEmpty()) out.add(sb.toString().trim())
            out.filter { it.isNotEmpty() }
        } else {
            // Minden sor külön bekezdés
            lines.map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
