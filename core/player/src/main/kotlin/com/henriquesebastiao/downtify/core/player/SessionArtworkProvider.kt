package com.henriquesebastiao.downtify.core.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Opaque read-only image capabilities, not an exported network proxy or directory listing. */
class SessionArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        EntryPointAccessors.fromApplication(checkNotNull(context).applicationContext, ArtworkEntryPoint::class.java)
            .artwork().open(uri, mode)

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ArtworkEntryPoint {
    fun artwork(): SessionArtworkCache
}
