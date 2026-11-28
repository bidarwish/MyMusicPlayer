package com.example.mymusicplayer

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.mymusicplayer.data.MusicRepository
import com.example.mymusicplayer.data.Song
import com.example.mymusicplayer.playback.MusicService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        connectToService()
    }

    private fun connectToService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                setupListener()
                startPositionUpdates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupListener() {
        controller?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSong(mediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.value = _uiState.value.copy(
                    duration = controller?.duration?.coerceAtLeast(0) ?: 0L
                )
            }
        })
        updateCurrentSong(controller?.currentMediaItem)
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    _uiState.value = _uiState.value.copy(
                        isPlaying = c.isPlaying,
                        position = c.currentPosition.coerceAtLeast(0),
                        duration = c.duration.coerceAtLeast(0),
                        shuffleMode = c.shuffleModeEnabled,
                        repeatMode = c.repeatMode
                    )
                }
                delay(500)
            }
        }
    }

    private fun updateCurrentSong(mediaItem: MediaItem?) {
        val song = if (mediaItem != null) {
            _songs.value.find { it.id.toString() == mediaItem.mediaId }
        } else null
        _uiState.value = _uiState.value.copy(currentSong = song)
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val loaded = MusicRepository.loadSongs(getApplication())
                _songs.value = loaded
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playSong(song: Song) {
        val allSongs = _songs.value
        if (allSongs.isEmpty()) return
        val index = allSongs.indexOfFirst { it.id == song.id }
        if (index < 0) return

        val mediaItems = allSongs.map {
            MediaItem.Builder()
                .setUri(it.uri)
                .setMediaId(it.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(it.albumArtUri)
                        .build()
                )
                .build()
        }
        controller?.setMediaItems(mediaItems, index, 0L)
        controller?.prepare()
        controller?.play()
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun playNext() {
        controller?.seekToNextMediaItem()
    }

    fun playPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
