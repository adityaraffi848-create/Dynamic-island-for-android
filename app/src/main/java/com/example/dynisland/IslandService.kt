package com.example.dynisland

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import android.view.WindowManager.LayoutParams as LP

class IslandService : Service() {
    companion object { @Volatile var running = false }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var lp: LP
    private var outer: FrameLayout? = null
    private lateinit var pill: FrameLayout
    private lateinit var small: LinearLayout
    private lateinit var big: LinearLayout
    private lateinit var bg: GradientDrawable
    private lateinit var artS: ImageView
    private lateinit var artB: ImageView
    private lateinit var tvMid: TextView
    private lateinit var tvTimeS: TextView
    private lateinit var tvTimeB: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvLyric: TextView
    private lateinit var tvPlay: TextView

    private var notch = true
    private var expanded = false
    private var compW = 0; private var compH = 0; private var expW = 0; private var expH = 0
    private var anim: ValueAnimator? = null
    private var controller: MediaController? = null
    private var lyrics: List<Pair<Long, String>> = emptyList()
    private var lyricKey = ""
    private var lastLine = ""
    private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
    private var dragging = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startFg()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        running = true
        build()
        return START_STICKY
    }

    override fun onDestroy() { running = false; teardown(); super.onDestroy() }

    private fun startFg() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("island", "Dynamic Island", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "island")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Dynamic Island aktif").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    // ---------- UI ----------
    private fun clip(v: View, r: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, r) }
        }
        v.clipToOutline = true
    }

    private fun tv(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size; setTextColor(color); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun btn(t: String, a: () -> Unit) = TextView(this).apply {
        text = t; textSize = 20f; setTextColor(Color.WHITE)
        setPadding(dp(20), dp(4), dp(20), dp(4))
        setOnClickListener { a(); armCollapse() }
    }

    private val autoCollapse = Runnable { setExpanded(false) }
    private fun armCollapse() { ui.removeCallbacks(autoCollapse); ui.postDelayed(autoCollapse, 7000) }

    private fun build() {
        teardown()
        val sp = getSharedPreferences("p", MODE_PRIVATE)
        notch = sp.getString("mode", "notch") == "notch"
        compW = dp(sp.getInt("w", 150)); compH = dp(34)
        expW = minOf(dp(340), resources.displayMetrics.widthPixels - dp(24)); expH = dp(190)

        bg = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = compH / 2f }
        pill = FrameLayout(this).apply { background = bg }

        // --- tampilan kecil (compact) ---
        small = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(14), 0)
        }
        artS = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        clip(artS, dp(11).toFloat())
        small.addView(artS, LinearLayout.LayoutParams(dp(22), dp(22)))
        tvMid = tv(11f, 0xCCFFFFFF.toInt()).apply { gravity = Gravity.CENTER; setPadding(dp(6), 0, dp(6), 0) }
        small.addView(tvMid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tvTimeS = tv(12f, Color.WHITE, true)
        small.addView(tvTimeS)
        pill.addView(small, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, compH, Gravity.TOP))

        // --- tampilan besar (pemutar musik) ---
        big = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(18), dp(14), dp(18), dp(8))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        artB = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF222222.toInt()) }
        clip(artB, dp(12).toFloat())
        row.addView(artB, LinearLayout.LayoutParams(dp(56), dp(56)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvTitle = tv(16f, Color.WHITE, true); tvArtist = tv(13f, 0xFFAAAAAA.toInt())
        col.addView(tvTitle); col.addView(tvArtist)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
        tvTimeB = tv(14f, Color.WHITE, true)
        row.addView(tvTimeB)
        big.addView(row)

        tvLyric = TextView(this).apply {
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2
            ellipsize = TextUtils.TruncateAt.END; text = "♪"
        }
        big.addView(tvLyric, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val ctrl = LinearLayout(this).apply { gravity = Gravity.CENTER }
        ctrl.addView(btn("◀◀") { controller?.transportControls?.skipToPrevious() })
        tvPlay = btn("▶") {
            val c = controller
            if (c?.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c?.transportControls?.play()
        }
        ctrl.addView(tvPlay)
        ctrl.addView(btn("▶▶") { controller?.transportControls?.skipToNext() })
        big.addView(ctrl)
        pill.addView(big, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val o = FrameLayout(this)
        o.addView(pill, FrameLayout.LayoutParams(compW, compH))
        o.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_OUTSIDE -> setExpanded(false)
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; dragging = false }
                MotionEvent.ACTION_MOVE -> if (!notch && !expanded) {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || abs(dx) + abs(dy) > dp(8)) {
                        dragging = true
                        lp.x = (startX + dx).toInt(); lp.y = (startY + dy).toInt()
                        wm.updateViewLayout(o, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) sp.edit().putInt("fx", lp.x).putInt("fy", lp.y).apply()
                    else setExpanded(!expanded)
                }
            }
            true
        }

        lp = LP(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL or LP.FLAG_WATCH_OUTSIDE_TOUCH or
                LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (notch) { x = 0; y = dp(sp.getInt("oy", 4)) }
            else { x = sp.getInt("fx", 0); y = sp.getInt("fy", dp(60)) }
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        outer = o
        wm.addView(o, lp)

        attachMedia()
        ui.post(tick)
    }

    private fun setExpanded(e: Boolean) {
        if (expanded == e) return
        expanded = e
        ui.removeCallbacks(autoCollapse)
        val w0 = pill.width; val h0 = pill.height
        val w1 = if (e) expW else compW; val h1 = if (e) expH else compH
        val r0 = bg.cornerRadius; val r1 = if (e) dp(30).toFloat() else compH / 2f
        if (e) { small.visibility = View.GONE; big.visibility = View.VISIBLE; big.alpha = 0f }
        else { big.visibility = View.GONE; small.visibility = View.VISIBLE }
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320; interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                pill.layoutParams = pill.layoutParams.apply {
                    width = (w0 + (w1 - w0) * t).toInt(); height = (h0 + (h1 - h0) * t).toInt()
                }
                bg.cornerRadius = r0 + (r1 - r0) * t
                if (e) big.alpha = t.coerceIn(0f, 1f)
            }
            start()
        }
        if (e) armCollapse()
    }

    // ---------- jam + lirik ----------
    private val tick = object : Runnable {
        override fun run() {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            tvTimeS.text = t; tvTimeB.text = t
            val st = controller?.playbackState
            if (st != null && lyrics.isNotEmpty()) {
                val pos = if (st.state == PlaybackState.STATE_PLAYING)
                    st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
                else st.position
                val line = lyrics.lastOrNull { it.first <= pos }?.second ?: ""
                if (line != lastLine) { lastLine = line; tvLyric.text = line.ifBlank { "♪" } }
            }
            ui.postDelayed(this, 300)
        }
    }

    private fun parseLrc(s: String): List<Pair<Long, String>> {
        val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)](.*)""")
        return s.lines().mapNotNull { line ->
            val m = re.find(line.trim()) ?: return@mapNotNull null
            val ms = (m.groupValues[1].toLong() * 60000 + m.groupValues[2].toDouble() * 1000).toLong()
            ms to m.groupValues[3].trim()
        }.sortedBy { it.first }
    }

    private fun loadLyrics(title: String, artist: String) {
        val key = "$title|$artist"
        if (key == lyricKey) return
        lyricKey = key; lyrics = emptyList(); lastLine = ""
        tvLyric.text = "Mencari lirik…"
        Thread {
            val res: List<Pair<Long, String>> = try {
                val url = "https://lrclib.net/api/search?track_name=${Uri.encode(title)}&artist_name=${Uri.encode(artist)}"
                val c = URL(url).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "DynIsland/1.0")
                c.connectTimeout = 8000; c.readTimeout = 8000
                val arr = JSONArray(c.inputStream.bufferedReader().readText())
                var found: String? = null
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i).optString("syncedLyrics")
                    if (s.isNotBlank() && s != "null") { found = s; break }
                }
                if (found != null) parseLrc(found) else emptyList()
            } catch (e: Exception) { emptyList() }
            ui.post { if (lyricKey == key) { lyrics = res; if (res.isEmpty()) tvLyric.text = "Lirik tidak ditemukan" } }
        }.start()
    }

    // ---------- media (Spotify & pemutar lain) ----------
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(m: MediaMetadata?) { refreshMeta() }
        override fun onPlaybackStateChanged(s: PlaybackState?) { refreshState() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it ?: emptyList()) }

    private fun attachMedia() {
        val msm = getSystemService(MediaSessionManager::class.java)
        val cn = ComponentName(this, MediaListener::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
            pick(msm.getActiveSessions(cn))
        } catch (e: SecurityException) {
            tvMid.text = "Izinkan akses notifikasi"
            tvTitle.text = "Izinkan akses notifikasi"
        }
        refreshMeta(); refreshState()
    }

    private fun pick(list: List<MediaController>) {
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName == "com.spotify.music" }
            ?: list.firstOrNull()
        if (c?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(cb)
        controller = c
        c?.registerCallback(cb, ui)
        refreshMeta(); refreshState()
    }

    private fun refreshMeta() {
        val m = controller?.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        tvTitle.text = title ?: "Tidak ada musik"
        tvArtist.text = artist
        artB.setImageBitmap(art); artS.setImageBitmap(art)
        artS.visibility = if (title != null) View.VISIBLE else View.GONE
        if (!notch) tvMid.text = title ?: ""   // mode poni: tengah dikosongkan (area kamera)
        if (title != null) loadLyrics(title, artist)
        else { lyricKey = ""; lyrics = emptyList(); lastLine = ""; tvLyric.text = "♪" }
    }

    private fun refreshState() {
        tvPlay.text = if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) "❚❚" else "▶"
    }

    private fun teardown() {
        ui.removeCallbacksAndMessages(null)
        anim?.cancel()
        controller?.unregisterCallback(cb); controller = null
        try { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        outer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        outer = null; expanded = false; lyricKey = ""; lyrics = emptyList()
    }
}
