package com.henriquesebastiao.downtify.core.player

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkDataSourceTest {
    @Test fun `notification artwork can read a local file without using HTTP`() {
        fixture { file -> assertArrayEquals(BYTES, read(Uri.fromFile(file))) }
    }

    @Test fun `notification artwork can read a content provider without an HTTP URL`() {
        fixture { file ->
            val context = RuntimeEnvironment.getApplication()
            val provider = object : ContentProvider() {
                override fun onCreate(): Boolean = true
                override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = AssetFileDescriptor(
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                    0,
                    file.length(),
                )
                override fun getType(uri: Uri): String = "image/jpeg"
                override fun query(
                    uri: Uri,
                    projection: Array<out String>?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                    sortOrder: String?,
                ): Cursor? = null
                override fun insert(uri: Uri, values: ContentValues?): Uri? = null
                override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
                override fun update(
                    uri: Uri,
                    values: ContentValues?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                ): Int = 0
            }
            provider.attachInfo(
                context,
                ProviderInfo().apply {
                    authority = "fixture.artwork"
                    applicationInfo = context.applicationInfo
                },
            )
            ShadowContentResolver.registerProviderInternal("fixture.artwork", provider)
            assertArrayEquals(BYTES, read(Uri.parse("content://fixture.artwork/cover")))
        }
    }

    private fun read(uri: Uri): ByteArray {
        val context = RuntimeEnvironment.getApplication()
        val http = OkHttpDataSource.Factory(
            OkHttpClient.Builder().addInterceptor {
                error("Local artwork must not use HTTP")
            }.build(),
        )
        val source = PlayerModule.artworkDataSource(context, http).createDataSource()
        return try {
            source.open(DataSpec.Builder().setUri(uri).build())
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            source.close()
        }
    }

    private fun fixture(block: (File) -> Unit) {
        val file = File.createTempFile("artwork-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
        try {
            file.writeBytes(BYTES)
            block(file)
        } finally {
            file.delete()
        }
    }

    private companion object {
        val BYTES = byteArrayOf(1, 2, 3, 4, 5)
    }
}
