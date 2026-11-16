package de.karoofun.karoo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Pre-rendered takeover frames as PNG files. The Karoo app loads them itself through [FramesProvider],
 * so a takeover sends only a list of URIs instead of megabytes of bitmaps.
 */
class FrameStore(context: Context, private val art: TakeoverArt) {
    private val dir = File(context.filesDir, DIR).apply { mkdirs() }

    /** Frame URIs for one takeover, rendering the files first if they are missing. */
    @Synchronized
    fun frames(type: Takeover, variant: Int, width: Int, height: Int, count: Int): List<Uri> = (0 until count).map { i ->
        val file = File(dir, name(type, variant, width, height, count, i))
        if (!file.exists()) {
            val bitmap = art.renderFrame(type, width, height, i, count, variant)
            val tmp = File(dir, file.name + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            tmp.renameTo(file)
        }
        Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.name).build()
    }

    /** Drops frames of other sizes or durations, so the cache does not grow when settings change. */
    @Synchronized
    fun keepOnly(width: Int, height: Int, count: Int) {
        val suffix = "_${width}x${height}_${count}_"
        dir.listFiles()?.filter { !it.name.contains(suffix) }?.forEach { it.delete() }
    }

    private fun name(type: Takeover, variant: Int, width: Int, height: Int, count: Int, index: Int) =
        "${type.name.lowercase()}_${variant}_${width}x${height}_${count}_${index}_v$VERSION.png"

    companion object {
        const val AUTHORITY = "de.karoofun.karoo.frames"
        const val DIR = "frames"
        /** Bump when the art changes, so old cached frames are not shown anymore. */
        private const val VERSION = 1
    }
}

/** Read-only access to the cached frame PNGs, for the Karoo app that renders our RemoteViews. */
class FramesProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (mode != "r" || !NAME.matches(name)) throw FileNotFoundException(name)
        val file = File(File(context!!.filesDir, FrameStore.DIR), name)
        if (!file.exists()) throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        val NAME = Regex("[a-z]+_\\d+_\\d+x\\d+_\\d+_\\d+_v\\d+\\.png")
    }
}
