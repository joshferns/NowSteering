package com.nowsteering.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Serves the share snapshots (whole log, key lines) as files, so size does not matter. */
class LogFileProvider : ContentProvider() {
    companion object {
        const val FILE_NAME = "nowsteering_log.txt"
        const val KEYLINES_NAME = "nowsteering_keylines.txt"
        private val NAMES = setOf(FILE_NAME, KEYLINES_NAME)

        val URI: Uri = uri(FILE_NAME)
        fun uri(name: String): Uri = Uri.parse("content://com.nowsteering.app.logs/$name")
        fun file(ctx: Context, name: String) = File(ctx.cacheDir, name)
        fun shareFile(ctx: Context) = file(ctx, FILE_NAME)
    }

    private fun nameOf(uri: Uri): String =
        uri.lastPathSegment?.takeIf { it in NAMES } ?: throw FileNotFoundException(uri.toString())

    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(context!!, nameOf(uri)), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<out String>?, sel: String?, args: Array<out String>?, sort: String?): Cursor {
        val name = nameOf(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(name, file(context!!, name).length()))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, sel: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<out String>?) = 0
}
