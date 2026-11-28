package com.example.mymusicplayer.data

import android.content.ContentUris
import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
}
