package durus.salafi.bangladesh.service

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        var playerInstance: ExoPlayer? = null
            private set
        var currentService: PlaybackService? = null
            private set

        const val ACTION_REWIND_5S = "durus.action.REWIND_5S"
        const val ACTION_FORWARD_5S = "durus.action.FORWARD_5S"

        fun getOrCreatePlayer(context: android.content.Context): ExoPlayer {
            return playerInstance ?: ExoPlayer.Builder(context.applicationContext)
                .setSeekBackIncrementMs(5000L)
                .setSeekForwardIncrementMs(5000L)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .build().also { playerInstance = it }
        }
    }

    override fun onCreate() {
        super.onCreate()
        currentService = this

        val exoPlayer = getOrCreatePlayer(this)

        val rewindCommand = SessionCommand(ACTION_REWIND_5S, Bundle.EMPTY)
        val forwardCommand = SessionCommand(ACTION_FORWARD_5S, Bundle.EMPTY)

        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 5s")
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(rewindCommand)
            .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Forward 5s")
            .setIconResId(android.R.drawable.ic_media_ff)
            .setSessionCommand(forwardCommand)
            .build()

        val sessionCallback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_REWIND_5S -> {
                        session.player.seekBack()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_FORWARD_5S -> {
                        session.player.seekForward()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .build()
                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    connectionResult.availablePlayerCommands
                )
            }
        }

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(sessionCallback)
            .setCustomLayout(listOf(rewindButton, forwardButton))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        playerInstance = null
        currentService = null
        super.onDestroy()
    }
}
