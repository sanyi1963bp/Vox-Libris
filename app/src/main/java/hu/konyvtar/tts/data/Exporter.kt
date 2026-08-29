package hu.konyvtar.tts.data

import android.content.Context
import android.os.Environment
import hu.konyvtar.tts.model.displayPercent
import hu.konyvtar.tts.model.statusText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import hu.konyvtar.tts.R

/**
 * Az olvasási nyilvántartás kimentése a telefon Letöltések mappájába,
 * ahonnan USB-kábellel egyszerűen átmásolható a PC-re.
 *
 * Három fájl készül:
 *  - olvasas_*.csv      : könyvenkénti haladás (Excel/LibreOffice-ban nyitható)
 *  - konyvjelzok_*.csv  : minden könyvjelző
 *  - konyvtar_tts_*.db  : az app teljes adatbázisa (SQLite, PC-n is olvasható)
 */
object Exporter {

    data class Result(
        val dir: String,
        val files: List<File>,
        val bookCount: Int,
        val finishedCount: Int,
        val bookmarkCount: Int
    )

    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    private val dateFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())

    /** Cellák pontosvesszős CSV-hez (a magyar Excel ezt várja). */
    private fun cell(value: Any?): String {
        val s = (value?.toString() ?: "")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace("\"", "\"\"")
        return "\"$s\""
    }

    private fun row(vararg values: Any?): String =
        values.joinToString(";") { cell(it) } + "\r\n"

    private fun targetDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "KonyvtarTTS")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Kimenti az olvasási nyilvántartást. Hiba esetén kivételt dob,
     * amit a hívó magyar üzenetként mutat meg.
     */
    fun exportAll(context: Context): Result {
        AppDb.init(context)
        val dir = targetDir()
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception(context.getString(R.string.build_error_open, dir.absolutePath))
        }
        val stamp = stampFmt.format(Date())
        val written = ArrayList<File>()

        // --- Olvasási haladás
        val progress = AppDb.allProgress()
        val progressFile = File(dir, "olvasas_$stamp.csv")
        StringBuilder().apply {
            append('﻿') // BOM, hogy az Excel felismerje az UTF-8-at
            append(
                row(
                    context.getString(R.string.csv_status),
                    context.getString(R.string.csv_title),
                    context.getString(R.string.csv_author),
                    context.getString(R.string.csv_percent),
                    context.getString(R.string.csv_paragraph),
                    context.getString(R.string.csv_total_paragraphs),
                    context.getString(R.string.csv_minutes),
                    context.getString(R.string.csv_last),
                    context.getString(R.string.csv_file),
                    context.getString(R.string.csv_book_id)
                )
            )
            for (p in progress) {
                append(
                    row(
                        p.statusText(context),
                        p.title,
                        p.author,
                        String.format(Locale.US, "%.1f", p.displayPercent()),
                        maxOf(p.paraIndex, p.readPara) + 1,
                        p.totalParas,
                        p.listenedMs / 60000,
                        if (p.lastAccess > 0) dateFmt.format(Date(p.lastAccess)) else "",
                        p.path,
                        p.konyvId ?: ""
                    )
                )
            }
            progressFile.writeText(toString(), Charsets.UTF_8)
        }
        written.add(progressFile)

        // --- Könyvjelzők
        val bookmarks = AppDb.allBookmarks()
        val bmFile = File(dir, "konyvjelzok_$stamp.csv")
        StringBuilder().apply {
            append('﻿')
            append(
                row(
                    context.getString(R.string.csv_title),
                    context.getString(R.string.csv_author),
                    context.getString(R.string.csv_paragraph),
                    context.getString(R.string.csv_snippet),
                    context.getString(R.string.csv_created),
                    context.getString(R.string.csv_file)
                )
            )
            for (b in bookmarks) {
                append(
                    row(
                        b.title,
                        b.author,
                        b.paraIndex + 1,
                        b.snippet,
                        if (b.created > 0) dateFmt.format(Date(b.created)) else "",
                        b.path
                    )
                )
            }
            bmFile.writeText(toString(), Charsets.UTF_8)
        }
        written.add(bmFile)

        // --- Teljes adatbázis másolata (SQLite)
        try {
            val src = AppDb.databaseFile()
            if (src.exists()) {
                val dbCopy = File(dir, "konyvtar_tts_$stamp.db")
                src.copyTo(dbCopy, overwrite = true)
                written.add(dbCopy)
            }
        } catch (_: Exception) {
            // ha az adatbázis másolása nem megy, a CSV-k már megvannak
        }

        return Result(
            dir = dir.absolutePath,
            files = written,
            bookCount = progress.size,
            finishedCount = progress.count { it.displayPercent() >= hu.konyvtar.tts.model.FINISHED_PERCENT },
            bookmarkCount = bookmarks.size
        )
    }
}
