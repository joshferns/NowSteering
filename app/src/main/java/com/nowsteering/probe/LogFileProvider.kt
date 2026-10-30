package com.nowsteering.probe

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Serves the share snapshot of the log so it can be sent as a file (no AndroidX FileProvider needed). */
class LogFileProvider : ContentProvider() {
    companion object {
        const val FILE_NAME = "nowsteering_probe_log.txt"
        val URI: Uri = Uri.parse("content://com.nowsteering.probe.logs/$FILE_NAME")
        fun shareFile(ctx: android.content.Context) = File(ctx.cacheDir, FILE_NAME)
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(shareFile(context!!), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<out String>?, sel: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = shareFile(context!!)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(FILE_NAME, f.length()))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<out String>?) = 0
}
