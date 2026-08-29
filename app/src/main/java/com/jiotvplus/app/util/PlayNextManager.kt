package com.jiotvplus.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
import com.jiotvplus.app.data.model.Channel

object PlayNextManager {

    private const val TAG = "PlayNextManager"

    /**
     * Inserts or updates a Watch Next program entry for the given channel.
     * Deduplicates by contentId stored in COLUMN_INTERNAL_PROVIDER_DATA.
     * Moves the channel to the top of the "Play Next" row on the Android TV home screen.
     */
    fun updateWatchNext(context: Context, channel: Channel) {
        try {
            val resolver = context.contentResolver

            // Remove any existing entry for this contentId to avoid duplicates
            removeWatchNext(context, channel.contentId)

            val intentUri = buildIntentUri(context.packageName, channel.contentId)

            val values = ContentValues().apply {
                put(TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.TYPE_CHANNEL)
                put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
                put(TvContract.WatchNextPrograms.COLUMN_TITLE, channel.name)
                put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, intentUri)
                put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_DATA, channel.contentId.toByteArray())
                if (!channel.thumbnail.isNullOrBlank()) {
                    put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, channel.thumbnail)
                }
                put(TvContract.WatchNextPrograms.COLUMN_START_TIME_UTC_MILLIS, System.currentTimeMillis())
                channel.currentProgram?.let { prog ->
                    if (prog.title.isNotBlank()) {
                        put(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, prog.title)
                    }
                }
            }

            val uri = resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, values)
            Log.d(TAG, "Watch Next added for ${channel.name} (${channel.contentId}) → $uri")
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to write Watch Next: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update Watch Next: ${e.message}")
        }
    }

    /**
     * Removes a Watch Next entry matching the given contentId.
     */
    fun removeWatchNext(context: Context, contentId: String) {
        try {
            val resolver = context.contentResolver
            val projection = arrayOf(
                TvContract.WatchNextPrograms._ID,
                TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_DATA
            )
            resolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                projection,
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val idIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms._ID)
                    val dataIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_DATA)
                    if (dataIdx >= 0) {
                        val storedId = String(cursor.getBlob(dataIdx), Charsets.UTF_8)
                        if (storedId == contentId) {
                            val rowUri = Uri.withAppendedPath(
                                TvContract.WatchNextPrograms.CONTENT_URI,
                                cursor.getLong(idIdx).toString()
                            )
                            resolver.delete(rowUri, null, null)
                            Log.d(TAG, "Removed existing Watch Next for $contentId")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove Watch Next: ${e.message}")
        }
    }

    /**
     * Builds an intent URI that launches the app's MainActivity with a channel deep link.
     * Format: intent://jiotvplus/channel/{contentId}#Intent;package=...;component=...;end
     */
    private fun buildIntentUri(packageName: String, contentId: String): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.jiotvplus.app/channel/$contentId")
            setPackage(packageName)
        }
        return intent.toUri(Intent.URI_INTENT_SCHEME)
    }
}
