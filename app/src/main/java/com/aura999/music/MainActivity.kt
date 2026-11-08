package com.aura999.music

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private var svc: MusicService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val items = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var playPause: Button
    private lateinit var shuffleBtn: Button
    private lateinit var repeatBtn: Button
    private var dp = 1f

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as MusicService.LocalBinder).service()
            svc = s
            s.onChange = { refreshUI() }
            refreshUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            svc = null
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val s = svc
            if (s != null) {
                val d = s.duration()
                if (d > 0) {
                    seekBar.max = d
                    seekBar.progress = s.position()
                    timeText.text = fmt(s.position()) + " / " + fmt(d)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        val purple = Color.parseColor("#7C4DFF")
        val dark = Color.parseColor("#2A2150")
        val teal = Color.parseColor("#00897B")

        // goat with glow
        val glow = GradientDrawable()
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        glow.setGradientRadius(110 * dp)
        glow.setColors(intArrayOf(Color.parseColor("#AA7C4DFF"), Color.parseColor("#00000000")))
        val goatBox = FrameLayout(this)
        goatBox.background = glow
        goatBox.layoutParams = LinearLayout.LayoutParams((220 * dp).toInt(), (220 * dp).toInt())
        val goat = ImageView(this)
        goat.setImageResource(R.drawable.goat_logo)
        goat.scaleType = ImageView.ScaleType.FIT_CENTER
        goat.layoutParams = FrameLayout.LayoutParams((150 * dp).toInt(), (150 * dp).toInt(), Gravity.CENTER)
        goatBox.addView(goat)

        val title = TextView(this)
        title.text = "Aura999"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        status = TextView(this)
        status.text = "No song selected"
        status.textSize = 15f
        status.setTextColor(Color.parseColor("#B388FF"))
        status.gravity = Gravity.CENTER
        status.maxLines = 1
        status.ellipsize = TextUtils.TruncateAt.END
        status.setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())

        seekBar = SeekBar(this)
        seekBar.setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) svc?.seekTo(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        timeText = TextView(this)
        timeText.text = "0:00 / 0:00"
        timeText.textSize = 13f
        timeText.setTextColor(Color.LTGRAY)
        timeText.gravity = Gravity.CENTER

        val prev = styledButton("Prev", dark) { ensureStarted(); svc?.previous() }
        playPause = styledButton("Play", purple) { ensureStarted(); svc?.togglePlay() }
        val next = styledButton("Next", dark) { ensureStarted(); svc?.next(false) }
        shuffleBtn = styledButton("Shuffle: off", dark) { svc?.toggleShuffle() }
        repeatBtn = styledButton("Repeat: off", dark) { svc?.cycleRepeat() }
        val add = styledButton("Add songs", teal) { pickSongs() }

        val row1 = row(prev, playPause, next)
        val row2 = row(shuffleBtn, repeatBtn)
        val row3 = row(add)

        adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.text = (position + 1).toString() + ". " + items[position]
                v.textSize = 15f
                v.setTextColor(if (svc?.index == position) Color.parseColor("#B388FF") else Color.LTGRAY)
                return v
            }
        }
        val list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            ensureStarted()
            svc?.playIndex(position, true)
        }
        list.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.parseColor("#0B0B14"))
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        root.addView(goatBox)
        root.addView(title)
        root.addView(status)
        root.addView(seekBar)
        root.addView(timeText)
        root.addView(row1)
        root.addView(row2)
        root.addView(row3)
        root.addView(list)
        setContentView(root)

        bindService(Intent(this, MusicService::class.java), conn, Context.BIND_AUTO_CREATE)
        handler.post(ticker)
    }

    private fun pill(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.setCornerRadius(24 * dp)
        return g
    }

    private fun styledButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setAllCaps(false)
        b.setTextColor(Color.WHITE)
        b.background = pill(color)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val m = (4 * dp).toInt()
        lp.setMargins(m, m, m, m)
        b.layoutParams = lp
        return b
    }

    private fun row(vararg buttons: Button): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        r.setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        for (b in buttons) r.addView(b)
        return r
    }

    private fun ensureStarted() {
        startService(Intent(this, MusicService::class.java))
    }

    private fun fmt(ms: Int): String {
        val s = ms / 1000
        return String.format("%d:%02d", s / 60, s % 60)
    }

    private fun refreshUI() {
        val s = svc ?: return
        status.text = if (s.index >= 0) s.currentName() else "No song selected"
        playPause.text = if (s.isPlaying()) "Pause" else "Play"
        shuffleBtn.text = "Shuffle: " + (if (s.shuffle) "on" else "off")
        repeatBtn.text = "Repeat: " + arrayOf("off", "all", "one")[s.repeatMode]
        items.clear()
        items.addAll(s.names)
        adapter.notifyDataSetChanged()
    }

    private fun pickSongs() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "audio/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != Activity.RESULT_OK || data == null) return
        val picked = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri)
        } else {
            data.data?.let { picked.add(it) }
        }
        for (u in picked) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
        }
        ensureStarted()
        svc?.addSongs(picked)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        svc?.onChange = null
        try {
            unbindService(conn)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
