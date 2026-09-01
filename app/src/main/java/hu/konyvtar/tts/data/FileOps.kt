package hu.konyvtar.tts.data

import android.content.Context
import hu.konyvtar.tts.R
import java.io.File

/**
 * Fájlműveletek a könyveken: átnevezés, áthelyezés, másolás, törlés.
 *
 * A lényeg nem a fájlmozgatás — azt egy fájlkezelő is tudja. A lényeg, hogy
 * **minden hozzá kötött adat is követi a fájlt**: a katalógusbejegyzés, az
 * olvasási haladás, a könyvjelzők, a jegyzet és a bélyegkép. Ha ugyanezt egy
 * fájlkezelőben csinálnád, mindez csendben elveszne, és a könyv újként
 * jelenne meg, nulláról.
 */
object FileOps {

    /** A művelet eredménye. Hiba esetén a szöveg a felhasználó nyelvén jön. */
    sealed class Result {
        /** Sikerült; [path] a fájl új helye (törlésnél üres). */
        data class Ok(val path: String) : Result()
        data class Error(val messageRes: Int) : Result()
    }

    /** Fájlnévben nem használható jelek. */
    private val forbidden = Regex("[/\\\\:*?\"<>|]")

    fun isValidName(name: String): Boolean =
        name.isNotBlank() && !forbidden.containsMatchIn(name) &&
            name != "." && name != ".." && name.length <= 200

    // ---------------------------------------------------------------- műveletek

    /** Átnevezés a fájl saját mappáján belül. */
    fun rename(context: Context, path: String, newName: String): Result {
        val src = File(path)
        if (!src.exists()) return Result.Error(R.string.fileops_err_missing)
        if (!isValidName(newName)) return Result.Error(R.string.fileops_err_name)
        val dst = File(src.parentFile, newName)
        if (dst.absolutePath == src.absolutePath) return Result.Ok(path)
        if (dst.exists()) return Result.Error(R.string.fileops_err_exists)
        return try {
            if (!src.renameTo(dst)) return Result.Error(R.string.fileops_err_failed)
            follow(context, path, dst.absolutePath.replace('\\', '/'))
            Result.Ok(dst.absolutePath.replace('\\', '/'))
        } catch (e: Exception) {
            Result.Error(R.string.fileops_err_failed)
        }
    }

    /** Áthelyezés másik mappába, a fájlnév megtartásával. */
    fun move(context: Context, path: String, targetDir: String): Result {
        val src = File(path)
        if (!src.exists()) return Result.Error(R.string.fileops_err_missing)
        val dir = File(targetDir)
        if (!dir.isDirectory) return Result.Error(R.string.fileops_err_folder)
        val dst = File(dir, src.name)
        if (dst.absolutePath == src.absolutePath) return Result.Ok(path)
        if (dst.exists()) return Result.Error(R.string.fileops_err_exists)
        return try {
            // Köteten belül átnevezés; SD-kártyára viszont másolni kell
            val moved = src.renameTo(dst) || (src.copyTo(dst, false).exists() && src.delete())
            if (!moved) return Result.Error(R.string.fileops_err_failed)
            follow(context, path, dst.absolutePath.replace('\\', '/'))
            Result.Ok(dst.absolutePath.replace('\\', '/'))
        } catch (e: Exception) {
            Result.Error(R.string.fileops_err_failed)
        }
    }

    /** Másolás másik mappába. Az eredeti marad, ahol volt. */
    fun copy(context: Context, path: String, targetDir: String): Result {
        val src = File(path)
        if (!src.exists()) return Result.Error(R.string.fileops_err_missing)
        val dir = File(targetDir)
        if (!dir.isDirectory) return Result.Error(R.string.fileops_err_folder)
        val dst = File(dir, src.name)
        if (dst.absolutePath == src.absolutePath) return Result.Error(R.string.fileops_err_exists)
        if (dst.exists()) return Result.Error(R.string.fileops_err_exists)
        return try {
            src.copyTo(dst, false)
            val to = dst.absolutePath.replace('\\', '/')
            // A másolat ugyanannak a műnek egy másik fájlja; a haladást nem
            // örökli, hiszen az az eredeti könyvhöz tartozik
            Catalog.addCopy(path, to)
            CoverStore.copyPath(context, path, to)
            Result.Ok(to)
        } catch (e: Exception) {
            Result.Error(R.string.fileops_err_failed)
        }
    }

    /**
     * Végleges törlés: a fájl és minden hozzá tartozó adat. Nincs
     * visszavonás, ezért a felület mindig rákérdez, a fájlnevet is mutatva.
     */
    fun delete(context: Context, path: String): Result {
        val src = File(path)
        return try {
            if (src.exists() && !src.delete()) return Result.Error(R.string.fileops_err_failed)
            Catalog.deleteFile(path)
            AppDb.forgetPath(path)
            CoverStore.remove(context, path)
            Result.Ok("")
        } catch (e: Exception) {
            Result.Error(R.string.fileops_err_failed)
        }
    }

    /** A fájlhoz kötött adatok átvezetése az új útvonalra. */
    private fun follow(context: Context, from: String, to: String) {
        Catalog.updatePath(from, to)
        AppDb.movePath(from, to)
        CoverStore.movePath(context, from, to)
    }
}
