package com.aura999.music

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private var player: MediaPlayer? = null
    private lateinit var status: TextView
    private lateinit var playPause: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val goat = ImageView(this).apply {
            setImageResource(R.drawable.goat_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams((240 * dp).toInt(), (240 * dp).toInt())
        }
        val title = TextView(this).apply {
            text = "Aura999"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }
        status = TextView(this).apply {
            text = "No song selected"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (16 * dp).toInt())
        }
        val choose = Button(this).apply {
            text = "Choose song"
            setOnClickListener { pickSong() }
        }
        playPause = Button(this).apply {
            text = "Play"
            isEnabled = false
            setOnClickListener { togglePlay() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(goat)
            addView(title)
            addView(status)
            addView(choose)
            addView(playPause)
        }
        setContentView(root)
    }

    private fun pickSong() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            loadSong(uri)
        }
    }

    private fun loadSong(uri: Uri) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
                setOnCompletionListener { playPause.text = "Play" }
            }
            status.text = songName(uri)
            playPause.isEnabled = true
            playPause.text = "Play"
        } catch (e: Exception) {
            status.text = "Could not open this file"
            playPause.isEnabled = false
        }
    }

    private fun songName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i)
        }
        return "Selected song"
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            playPause.text = "Play"
        } else {
            p.start()
            playPause.text = "Pause"
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
