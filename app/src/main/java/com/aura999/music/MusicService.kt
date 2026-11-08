package com.aura999.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.OpenableColumns

class MusicService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): MusicService = this@MusicService
    }

    companion object {
        const val CHANNEL = "aura999_playback"
        const val NOTIF_ID = 999
        const val ACTION_PLAY_PAUSE = "com.aura999.music.PLAY_PAUSE"
        const val ACTION_NEXT = "com.aura999.music.NEXT"
        const val ACTION_PREV = "com.aura999.music.PREV"
        const val ACTION_STOP = "com.aura999.music.STOP"
    }

    private val binder = LocalBinder()
    private var player: MediaPlayer? = null
    private var largeIcon: Bitmap? = null
    private var foregroundStarted = false
    private var everPlayed = false

    val uris = ArrayList<Uri>()
    val names = ArrayList<String>()
    var index = -1
    var shuffle = false
    var repeatMode = 0 // 0 = off, 1 = all, 2 = one
    var onChange: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        largeIcon = loadLargeIcon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlay()
            ACTION_NEXT -> next(false)
            ACTION_PREV -> previous()
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaying()) stopAll()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        super.onDestroy()
    }

    // ---------- playlist and playback ----------

    fun addSongs(list: List<Uri>) {
        for (u in list) {
            uris.add(u)
            names.add(nameOf(u))
        }
        if (index == -1 && uris.isNotEmpty()) playIndex(0, false) else update()
    }

    fun playIndex(i: Int, start: Boolean) {
        if (i < 0 || i >= uris.size) return
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        index = i
        val mp = MediaPlayer()
        try {
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, uris[i])
            mp.prepare()
            mp.setOnCompletionListener { onSongEnd() }
            player = mp
            if (start) mp.start()
        } catch (e: Exception) {
            mp.release()
            player = null
        }
        update()
    }

    private fun onSongEnd() {
        if (repeatMode == 2) playIndex(index, true) else next(true)
    }

    fun next(auto: Boolean) {
        if (uris.isEmpty()) return
        var n = index + 1
        if (shuffle && uris.size > 1) {
            n = index
            while (n == index) n = (0 until uris.size).random()
        }
        if (n >= uris.size) {
            if (repeatMode == 1 || !auto) playIndex(0, true) else playIndex(0, false)
            return
        }
        playIndex(n, true)
    }

    fun previous() {
        if (uris.isEmpty()) return
        val p = player
        if (p != null && position() > 3000) {
            p.seekTo(0)
            return
        }
        val n = if (index - 1 < 0) uris.size - 1 else index - 1
        playIndex(n, true)
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.start()
        update()
    }

    fun seekTo(ms: Int) {
        try {
            player?.seekTo(ms)
        } catch (e: Exception) {
        }
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        update()
    }

    fun cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        update()
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (e: Exception) {
        false
    }

    fun position(): Int = try {
        player?.currentPosition ?: 0
    } catch (e: Exception) {
        0
    }

    fun duration(): Int = try {
        player?.duration ?: 0
    } catch (e: Exception) {
        0
    }

    fun currentName(): String = if (index in names.indices) names[index] else ""

    private fun stopAll() {
        try {
            player?.pause()
        } catch (e: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        onChange?.invoke()
        stopSelf()
    }

    // ---------- notification ----------

    private fun update() {
        onChange?.invoke()
        if (index < 0 || player == null) return
        val playing = isPlaying()
        if (playing) everPlayed = true
        val n = buildNotification(playing)
        val nm = getSystemService(NotificationManager::class.java)
        if (playing) {
            if (!foregroundStarted) {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIF_ID, n)
                    }
                    foregroundStarted = true
                } catch (e: Exception) {
                    nm.notify(NOTIF_ID, n)
                }
            } else {
                nm.notify(NOTIF_ID, n)
            }
        } else {
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_DETACH)
                foregroundStarted = false
            }
            if (everPlayed) nm.notify(NOTIF_ID, n)
        }
    }

    private fun buildNotification(playing: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val state = if (playing) "Playing" else "Paused"
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentName())
            .setContentText("Aura999 - " + state + " - " + (index + 1) + " of " + uris.size)
            .setContentIntent(openPi)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
        largeIcon?.let { b.setLargeIcon(it) }
        b.addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
        val mid = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        b.addAction(action(mid, if (playing) "Pause" else "Play", ACTION_PLAY_PAUSE))
        b.addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
        b.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP))
        return b.build()
    }

    private fun action(icon: Int, title: String, act: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, act.hashCode(),
            Intent(this, MusicService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun loadLargeIcon(): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inSampleSize = 4
            val b = BitmapFactory.decodeResource(resources, R.drawable.goat_logo, o) ?: return null
            val s = 256f / maxOf(b.width, b.height)
            if (s < 1f) {
                Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
            } else {
                b
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun nameOf(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) return c.getString(i)
            }
        } catch (e: Exception) {
        }
        return "Song"
    }
}
