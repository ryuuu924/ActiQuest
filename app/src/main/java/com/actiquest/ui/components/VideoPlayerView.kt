package com.actiquest.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.actiquest.ui.theme.DarkNavyBackground
import com.actiquest.ui.theme.SurfaceCharcoal

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUriString: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isError by remember { mutableStateOf(false) }

    val resolvedPlayableUrl = remember(videoUriString) {
        com.actiquest.data.VideoStorageManager.resolvePlayableUrl(context, videoUriString)
    }

    val exoPlayer = remember(resolvedPlayableUrl) {
        ExoPlayer.Builder(context).build().apply {
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(resolvedPlayableUrl))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isError = true
                        try {
                            val fallbackItem = MediaItem.fromUri(Uri.parse(com.actiquest.data.VideoStorageManager.DEFAULT_FALLBACK_VIDEO))
                            setMediaItem(fallbackItem)
                            prepare()
                            playWhenReady = true
                        } catch (_: Exception) {}
                    }
                })
            } catch (e: Exception) {
                isError = true
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCharcoal),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
