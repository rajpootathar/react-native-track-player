package com.doublesymmetry.kotlinaudio.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.RequiresApi
import com.doublesymmetry.kotlinaudio.R
import com.doublesymmetry.kotlinaudio.event.NotificationEventHolder
import com.doublesymmetry.kotlinaudio.models.*
import com.doublesymmetry.kotlinaudio.utils.isJUnitTest
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationManager internal constructor(private val context: Context, private val exoPlayer: ExoPlayer, private val event: NotificationEventHolder) : PlayerNotificationManager.PrimaryActionReceiver, PlayerNotificationManager.NotificationListener {
    private var descriptionAdapter: DescriptionAdapter? = null
    private var internalManager: PlayerNotificationManager? = null

    private val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "AudioPlayerSession")
    private val mediaSessionConnector: MediaSessionConnector = MediaSessionConnector(mediaSession)

    private val scope = CoroutineScope(Dispatchers.Main)

    private val buttons = mutableSetOf<NotificationButton?>()

    private val channelId: String

    var notificatioMetadata: NotificationMetadata? = null
        set(value) {
            field = value
            internalManager?.invalidate()
        }

    var ratingType: Int = RatingCompat.RATING_NONE
        set(value) {
            field = value
            mediaSession.setRatingType(ratingType)
            mediaSessionConnector.setRatingCallback(object: MediaSessionConnector.RatingCallback {
                override fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean {
                    return true
                }

                override fun onSetRating(player: Player, rating: RatingCompat) {
                    event.updateOnMediaSessionCallbackTriggered(MediaSessionCallback.RATING(rating, null))
                }

                override fun onSetRating(player: Player, rating: RatingCompat, extras: Bundle?) {
                    event.updateOnMediaSessionCallbackTriggered(MediaSessionCallback.RATING(rating, extras))
                }

            })
        }

    var showPlayPauseButton: Boolean
        get() = internalManager?.usePlayPauseActions ?: false
        set(value) {
            internalManager?.usePlayPauseActions = value
        }

    var showStopButton: Boolean
        get() = internalManager?.useStopAction ?: false
        set(value) {
            internalManager?.useStopAction = value
        }

    var showStopButtonCompact: Boolean
        get() = internalManager?.useStopActionInCompactView ?: false
        set(value) {
            internalManager?.useStopActionInCompactView = value
        }

    var showForwardButton: Boolean
        get() = internalManager?.useFastForwardAction ?: false
        set(value) {
            internalManager?.useFastForwardAction = value
        }

    /**
     * Controls whether or not this button should appear when the notification is compact (collapsed).
     */
    var showForwardButtonCompact: Boolean
        get() = internalManager?.useFastForwardActionInCompactView ?: false
        set(value) {
            internalManager?.useFastForwardActionInCompactView = value
        }

    var showBackwardButton: Boolean
        get() = internalManager?.useRewindAction ?: false
        set(value) {
            internalManager?.useRewindAction = value
        }

    /**
     * Controls whether or not this button should appear when the notification is compact (collapsed).
     */
    var showBackwardButtonCompact: Boolean
        get() = internalManager?.useRewindActionInCompactView ?: false
        set(value) {
            internalManager?.useRewindActionInCompactView = value
        }

    var showNextButton: Boolean
        get() = internalManager?.useNextAction ?: false
        set(value) {
            internalManager?.useNextAction = value
        }

    /**
     * Controls whether or not this button should appear when the notification is compact (collapsed).
     */
    var showNextButtonCompact: Boolean
        get() = internalManager?.useNextActionInCompactView ?: false
        set(value) {
            internalManager?.useNextActionInCompactView = value
        }

    var showPreviousButton: Boolean
        get() = internalManager?.usePreviousAction ?: false
        set(value) {
            internalManager?.usePreviousAction = value
        }

    /**
     * Controls whether or not this button should appear when the notification is compact (collapsed).
     */
    var showPreviousButtonCompact: Boolean
        get() = internalManager?.usePreviousActionInCompactView ?: false
        set(value) {
            internalManager?.usePreviousActionInCompactView = value
        }

    init {
        channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        if (!isJUnitTest()) {
            mediaSessionConnector.setPlayer(exoPlayer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = CHANNEL_ID
        val channelName = context.getString(R.string.playback_channel_name)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.description = "Used when playing music"
        channel.setSound(null, null)

        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    /**
     * Create a media player notification that automatically updates.
     *
     * **NOTE:** You should only call this once. Subsequent calls will result in an error.
     */
    fun createNotification(config: NotificationConfig) {
        buttons.apply {
            clear()
            addAll(config.buttons)
        }

        descriptionAdapter = DescriptionAdapter(object: NotificationMetadataProvider {
            override fun getTitle(): String? {
                return notificatioMetadata?.title
            }

            override fun getArtist(): String? {
                return notificatioMetadata?.artist
            }

            override fun getArtworkUrl(): String? {
                return notificatioMetadata?.artworkUrl
            }
        }, context, config.pendingIntent)

        internalManager = PlayerNotificationManager.Builder(context, NOTIFICATION_ID, channelId).apply {
            setMediaDescriptionAdapter(descriptionAdapter!!)
            setNotificationListener(this@NotificationManager)

            if (buttons.isNotEmpty()) {
                setPrimaryActionReceiver(this@NotificationManager)

                config.buttons.forEach { button ->
                    when (button) {
                        is NotificationButton.PLAY -> button.icon?.let { setPlayActionIconResourceId(it) }
                        is NotificationButton.PAUSE -> button.icon?.let { setPauseActionIconResourceId(it) }
                        is NotificationButton.STOP -> button.icon?.let { setStopActionIconResourceId(it) }
                        is NotificationButton.FORWARD -> button.icon?.let { setFastForwardActionIconResourceId(it) }
                        is NotificationButton.BACKWARD -> button.icon?.let { setRewindActionIconResourceId(it) }
                        is NotificationButton.NEXT -> button.icon?.let { setNextActionIconResourceId(it) }
                        is NotificationButton.PREVIOUS -> button.icon?.let { setPreviousActionIconResourceId(it) }
                    }
                }
            }
        }.build()

        if (!isJUnitTest()) {
            internalManager?.apply {
                setPlayer(exoPlayer)
                setColor(config.accentColor ?: Color.TRANSPARENT)
                config.smallIcon?.let { setSmallIcon(it) }

                config.buttons.forEach { button ->
                    when (button) {
                        is NotificationButton.PLAY, is NotificationButton.PAUSE -> showPlayPauseButton = true
                        is NotificationButton.STOP -> {
                            showStopButton = true
                            showStopButtonCompact = button.isCompact
                        }
                        is NotificationButton.FORWARD -> {
                            showForwardButton = true
                            showForwardButtonCompact = button.isCompact
                        }
                        is NotificationButton.BACKWARD -> {
                            showBackwardButton = true
                            showBackwardButtonCompact = button.isCompact
                        }
                        is NotificationButton.NEXT -> {
                            showNextButton = true
                            showNextButtonCompact = button.isCompact
                        }
                        is NotificationButton.PREVIOUS -> {
                            showPreviousButton = true
                            showPreviousButtonCompact = button.isCompact
                        }
                    }
                }

                setMediaSessionToken(mediaSession.sessionToken)
            }
        }
    }

    fun clearNotification() {
        mediaSession.isActive = false
        internalManager?.setPlayer(null)
    }

    override fun onAction(player: Player, action: String, intent: Intent) {
        scope.launch {
            event.updateOnNotificationButtonTapped(NotificationButton.valueOf(action))
        }
    }

    override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
        scope.launch {
            event.updateNotificationState(NotificationState.POSTED(notificationId, notification))
        }
    }

    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
        scope.launch {
            event.updateNotificationState(NotificationState.CANCELLED(notificationId))
        }
    }

    internal fun onPlay() {
        mediaSession.isActive = true
        reload()
    }

    internal fun onPause() {
        reload()
    }

    internal fun destroy() {
        descriptionAdapter?.release()
        internalManager?.setPlayer(null)
    }

    private fun reload() {
        internalManager?.invalidate()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "kotlin_audio_player"
    }
}