package com.example.dynisland

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import android.view.WindowManager.LayoutParams as LP

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private val C_PINK = 0xFFFF375F.toInt()
private val C_RED = 0xFFFF5A4D.toInt()
private val C_GREEN = 0xFF30D158.toInt()
private val C_WA = 0xFF25D366.toInt()
private val C_YEL = 0xFFFFD60A.toInt()
private val C_ORG = 0xFFFF9F0A.toInt()
private val C_GRAY = 0xFF8E8E93.toInt()
private val C_CARD = 0xFF2C2C2E.toInt()
private val C_DARK = 0xFF1C1C1E.toInt()

class IslandService : Service() {
    companion object { @Volatile var running = false }

    private enum class St { COMPACT, PANEL, HUD, BANNER }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var wp: LP
    private lateinit var sp: SharedPreferences
    private lateinit var am: AudioManager
    private var outer: FrameLayout? = null
    private lateinit var pill: FrameLayout
    private lateinit var bg: GradientDrawable
    private lateinit var small: LinearLayout
    private lateinit var hud: LinearLayout
    private lateinit var banner: LinearLayout
    private lateinit var big: FrameLayout
    private lateinit var pageBox: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var inputBar: LinearLayout
    private lateinit var edit: EditText
    private lateinit var ringS: RingView
    private lateinit var ringB: RingView
    private lateinit var tvMid: TextView
    private lateinit var tvTimeS: TextView
    private lateinit var tvHead: TextView
    private lateinit var tvDate: TextView
    private lateinit var hudIcon: TextView
    private lateinit var hudBar: BarView
    private lateinit var hudPct: TextView
    private lateinit var banAv: TextView
    private lateinit var banTitle: TextView
    private lateinit var banText: TextView
    // halaman musik
    private lateinit var pMusic: LinearLayout
    private lateinit var artB: ImageView
    private lateinit var tvNote: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvLyric: TextView
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var seekBar: BarView
    private lateinit var tvPlay: TextView
    // halaman timer
    private lateinit var pTimer: LinearLayout
    private lateinit var ringT: RingView
    private lateinit var tvTimer: TextView
    private lateinit var tvTMode: TextView
    private lateinit var tvTPlay: TextView
    // halaman chat
    private lateinit var pChat: LinearLayout
    private lateinit var chatRow: LinearLayout
    private lateinit var tvChatName: TextView
    private lateinit var tvChatCount: TextView
    private lateinit var tvChatMsgs: TextView
    private lateinit var tvChatEmpty: TextView
    // halaman kecerahan & suara
    private lateinit var pBright: LinearLayout
    private lateinit var briBar: BarView
    private lateinit var volBar: BarView
    private lateinit var tvBri: TextView
    private lateinit var tvVol: TextView
    private lateinit var btnPerm: TextView
    // halaman to-do
    private lateinit var pTodo: LinearLayout
    private lateinit var tvTodoCnt: TextView
    private lateinit var todoList: LinearLayout
    private lateinit var pages: Array<View>
    private lateinit var tabs: List<TextView>
    private lateinit var fmtT: SimpleDateFormat
    private lateinit var fmtD: SimpleDateFormat

    private var st = St.COMPACT
    private var notch = true
    private var topFlat = true
    private var sc = 1f
    private var oyDp = 0
    private var h24 = false
    private var compW = 0; private var compH = 0
    private var expW = 0; private var expH = 0
    private var hudW = 0; private var hudH = 0
    private var banW = 0; private var banH = 0
    private var curT = 0f; private var curB = 0f
    private var anim: ValueAnimator? = null
    private var pageIdx = 0
    private var bannerPage = 2
    private var chatIdx = 0
    private var inputCb: ((String) -> Unit)? = null
    private var downX = 0f; private var downY = 0f
    private var startX = 0; private var startY = 0
    private var dragging = false
    private var lastVol = -1; private var lastBri = -1
    // media
    private var controller: MediaController? = null
    private var lyrics: List<Pair<Long, String>> = emptyList()
    private var lyricKey = ""
    private var lastLine = ""
    private var lyricShow = true
    private var curDur = 0L
    // timer
    private var tMode = 0
    private var tTotal = 25 * 60_000L
    private var tLeft = tTotal
    private var tRun = false
    private var tEnd = 0L

    private val pageColors = intArrayOf(C_PINK, C_RED, C_WA, C_YEL, C_GREEN)
    private val heads = listOf("Sedang " to "Diputar", "Timer " to "Fokus", "Chat " to "WhatsApp", "Kecerahan " to "& Suara", "Todo" to "List")

    // ---------- util ----------
    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density * sc).toInt()
    private fun dpr(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lin(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun rounded(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = r.toFloat() }
    private fun oval(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }

    private fun clip(v: View, r: Int) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, r.toFloat()) }
        }
        v.clipToOutline = true
    }

    private fun tv(t: String = "", size: Float = 13f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size * sc; setTextColor(color); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun chip(t: String, a: () -> Unit) = tv(t, 12f, Color.WHITE).apply {
        gravity = Gravity.CENTER
        background = rounded(C_CARD, dp(16))
        setPadding(dp(9), dp(6), dp(9), dp(6))
        setOnClickListener { a(); arm() }
    }

    private fun btn(t: String, size: Float = 20f, a: () -> Unit) = tv(t, size, Color.WHITE).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(2), dp(14), dp(2))
        setOnClickListener { a(); arm() }
    }

    private fun fmtMs(ms: Long, up: Boolean = false): String {
        val s = (if (up) (ms + 999) / 1000 else ms / 1000).coerceAtLeast(0L).toInt()
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun setRad(t: Float, b: Float) {
        curT = t; curB = b
        bg.cornerRadii = floatArrayOf(t, t, t, t, b, b, b, b)
    }

    // ---------- lifecycle ----------
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        am = getSystemService(AudioManager::class.java)
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

    // ---------- membangun tampilan ----------
    private val autoBack = Runnable { go(St.COMPACT) }
    private fun arm() { if (!inputOn()) go(st) }
    private fun inputOn() = ::inputBar.isInitialized && inputBar.visibility == View.VISIBLE

    private fun build() {
        teardown()
        sp = getSharedPreferences("p", MODE_PRIVATE)
        notch = sp.getString("mode", "notch") == "notch"
        sc = sp.getInt("scale", 100) / 100f
        oyDp = sp.getInt("oy", 0)
        h24 = sp.getBoolean("h24", false)
        fmtT = SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Locale.getDefault())
        fmtD = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val maxW = resources.displayMetrics.widthPixels - dpr(16)
        compW = minOf(dp(sp.getInt("w", 150)), maxW); compH = dp(34)
        expW = minOf(dp(380), maxW); expH = dp(255)
        hudW = minOf(dp(280), maxW); hudH = dp(54)
        banW = minOf(dp(340), maxW); banH = dp(64)
        topFlat = notch && oyDp == 0
        lastVol = -1; lastBri = -1; lyricShow = true; chatIdx = 0

        bg = GradientDrawable().apply { setColor(Color.BLACK) }
        setRad(if (topFlat) 0f else compH / 2f, compH / 2f)
        pill = FrameLayout(this).apply { background = bg }

        // --- compact: ring baterai + jam ---
        small = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(14), 0) }
        ringS = RingView(this).apply { stroke = dp(2.5f).toFloat() }
        small.addView(ringS, lin(dp(18), dp(18)))
        tvMid = tv("", 11f, 0xCCFFFFFF.toInt()).apply { gravity = Gravity.CENTER; setPadding(dp(6), 0, dp(6), 0) }
        small.addView(tvMid, lin(0, WRAP, 1f))
        tvTimeS = tv("", 13f, Color.WHITE, true)
        small.addView(tvTimeS)
        pill.addView(small, FrameLayout.LayoutParams(MATCH, compH, Gravity.TOP))

        // --- HUD volume / kecerahan ---
        hud = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), 0, dp(18), 0); visibility = View.GONE }
        hudIcon = tv("🔊", 18f).apply { gravity = Gravity.CENTER }
        hud.addView(hudIcon, lin(dp(30), WRAP))
        hudBar = BarView(this, C_PINK)
        hud.addView(hudBar, lin(0, dp(10), 1f).apply { leftMargin = dp(10); rightMargin = dp(10) })
        hudPct = tv("0%", 13f, Color.WHITE, true).apply { gravity = Gravity.END }
        hud.addView(hudPct, lin(dp(40), WRAP))
        pill.addView(hud, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- banner notifikasi ---
        banner = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(16), 0); visibility = View.GONE }
        banAv = tv("W", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = oval(C_WA) }
        banner.addView(banAv, lin(dp(38), dp(38)))
        val bcol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        banTitle = tv("", 14f, Color.WHITE, true)
        banText = tv("", 12f, C_GRAY)
        bcol.addView(banTitle); bcol.addView(banText)
        banner.addView(bcol, lin(0, WRAP, 1f).apply { leftMargin = dp(10) })
        pill.addView(banner, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- panel besar ---
        big = FrameLayout(this).apply { visibility = View.GONE }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(30), dp(10), dp(30), dp(10)) }
        val head = FrameLayout(this)
        head.setOnClickListener { go(St.COMPACT) }
        tvHead = tv("", 15f, Color.WHITE, true)
        head.addView(tvHead, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
        val dateRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        ringB = RingView(this).apply { stroke = dp(2).toFloat() }
        dateRow.addView(ringB, lin(dp(14), dp(14)))
        tvDate = tv("", 12f, Color.WHITE, true).apply { setPadding(dp(6), 0, 0, 0) }
        dateRow.addView(tvDate)
        head.addView(dateRow, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
        col.addView(head, lin(MATCH, dp(26)))

        pageBox = FrameLayout(this)
        pMusic = mkMusic(); pTimer = mkTimer(); pChat = mkChat(); pBright = mkBright(); pTodo = mkTodo()
        pages = arrayOf(pMusic, pTimer, pChat, pBright, pTodo)
        pages.forEach { pageBox.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }
        col.addView(pageBox, lin(MATCH, 0, 1f))

        tabBar = LinearLayout(this).apply { gravity = Gravity.CENTER; background = rounded(C_DARK, dp(16)); setPadding(dp(4), dp(3), dp(4), dp(3)) }
        val glyphs = listOf("♪", "⏱\uFE0E", "✉\uFE0E", "☼", "✔\uFE0E", "⚙\uFE0E")
        tabs = glyphs.mapIndexed { i, g ->
            tv(g, 16f, C_GRAY).apply {
                gravity = Gravity.CENTER
                setPadding(dp(11), dp(4), dp(11), dp(4))
                setOnClickListener { if (i == 5) openSettings() else selectPage(i) }
            }
        }
        tabs.forEach { tabBar.addView(it) }
        col.addView(tabBar, lin(WRAP, dp(34)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4) })

        inputBar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE; background = rounded(C_DARK, dp(18)); setPadding(dp(14), 0, dp(2), 0) }
        edit = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(C_GRAY); textSize = 14f * sc
            background = null; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { submitInput(); true } else false }
        }
        inputBar.addView(edit, lin(0, MATCH, 1f))
        inputBar.addView(tv("➤", 18f, C_GREEN).apply { gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0); setOnClickListener { submitInput() } }, lin(WRAP, MATCH))
        col.addView(inputBar, lin(MATCH, dp(36)).apply { topMargin = dp(4) })

        big.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        fun chev(t: String, g: Int, d: Int) {
            val v = tv(t, 15f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = oval(0xFF48484A.toInt())
                setOnClickListener { selectPage((pageIdx + d + 5) % 5) }
            }
            big.addView(v, FrameLayout.LayoutParams(dp(22), dp(22), g or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(5); rightMargin = dp(5)
            })
        }
        chev("‹", Gravity.START, -1); chev("›", Gravity.END, 1)
        pill.addView(big, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- window ---
        val o = FrameLayout(this)
        o.addView(pill, FrameLayout.LayoutParams(compW, compH))
        o.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_OUTSIDE -> if (st == St.PANEL || st == St.BANNER) go(St.COMPACT)
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = wp.x; startY = wp.y; dragging = false }
                MotionEvent.ACTION_MOVE -> if (!notch && st == St.COMPACT) {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || abs(dx) + abs(dy) > dpr(8)) {
                        dragging = true
                        wp.x = (startX + dx).toInt(); wp.y = (startY + dy).toInt()
                        wm.updateViewLayout(o, wp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) sp.edit().putInt("fx", wp.x).putInt("fy", wp.y).apply()
                    else when (st) {
                        St.COMPACT -> go(St.PANEL)
                        St.BANNER -> openPage(bannerPage)
                        St.HUD -> go(St.COMPACT)
                        St.PANEL -> arm()
                    }
                }
            }
            true
        }
        wp = LP(
            WRAP, WRAP, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL or LP.FLAG_WATCH_OUTSIDE_TOUCH or
                LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (notch) { x = 0; y = dpr(oyDp) } else { x = sp.getInt("fx", 0); y = sp.getInt("fy", dpr(60)) }
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        outer = o
        wm.addView(o, wp)

        ChatStore.listener = { c -> ui.post { onNewChat(c) } }
        setTMode(0)
        selectPage(0)
        attachMedia()
        ui.post(tick)
    }

    // ---------- halaman: musik ----------
    private fun mkMusic(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val box = FrameLayout(this).apply { background = rounded(0xFF222222.toInt(), dp(12)) }
        clip(box, dp(12))
        artB = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        tvNote = tv("♫", 26f, C_GRAY).apply { gravity = Gravity.CENTER }
        box.addView(artB, FrameLayout.LayoutParams(MATCH, MATCH))
        box.addView(tvNote, FrameLayout.LayoutParams(MATCH, MATCH))
        top.addView(box, lin(dp(58), dp(58)))
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvTitle = tv("Tidak ada musik", 16f, Color.WHITE, true)
        tvArtist = tv("", 12f, C_GRAY)
        tvLyric = tv("", 13f, Color.WHITE)
        c.addView(tvTitle); c.addView(tvArtist)
        c.addView(tvLyric, lin(WRAP, WRAP).apply { topMargin = dp(3) })
        top.addView(c, lin(0, WRAP, 1f).apply { leftMargin = dp(12) })
        p.addView(top, lin(MATCH, WRAP))

        val prog = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tvPos = tv("00:00", 11f, C_GRAY); tvDur = tv("-00:00", 11f, C_GRAY)
        seekBar = BarView(this, Color.WHITE).apply {
            interactive = true
            onChange = { f, fin -> if (fin && curDur > 0) controller?.transportControls?.seekTo((f * curDur).toLong()) }
        }
        prog.addView(tvPos)
        prog.addView(seekBar, lin(0, dp(18), 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        prog.addView(tvDur)
        p.addView(prog, lin(MATCH, WRAP).apply { topMargin = dp(6) })

        val ctrl = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val lyBtn = tv("❝", 17f, C_PINK).apply {
            setPadding(dp(4), dp(2), dp(10), dp(2))
            setOnClickListener {
                lyricShow = !lyricShow
                tvLyric.visibility = if (lyricShow) View.VISIBLE else View.GONE
                setTextColor(if (lyricShow) C_PINK else C_GRAY)
                arm()
            }
        }
        val mid = LinearLayout(this).apply { gravity = Gravity.CENTER }
        mid.addView(btn("◀◀") { controller?.transportControls?.skipToPrevious() })
        tvPlay = btn("▶", 22f) {
            val c2 = controller
            if (c2?.playbackState?.state == PlaybackState.STATE_PLAYING) c2.transportControls.pause()
            else c2?.transportControls?.play()
        }
        mid.addView(tvPlay)
        mid.addView(btn("▶▶") { controller?.transportControls?.skipToNext() })
        val vol = tv("🔈", 16f, C_GRAY).apply {
            gravity = Gravity.END
            setPadding(dp(10), dp(2), dp(4), dp(2))
            setOnClickListener { selectPage(3) }
        }
        ctrl.addView(lyBtn, lin(0, WRAP, 1f))
        ctrl.addView(mid, lin(WRAP, WRAP))
        ctrl.addView(vol, lin(0, WRAP, 1f))
        p.addView(ctrl, lin(MATCH, WRAP).apply { topMargin = dp(2) })
        return p
    }

    // ---------- halaman: timer ----------
    private fun mkTimer(): LinearLayout {
        val p = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val rb = FrameLayout(this)
        ringT = RingView(this).apply { stroke = dp(6).toFloat() }
        rb.addView(ringT, FrameLayout.LayoutParams(MATCH, MATCH))
        rb.addView(tv("⏱\uFE0E", 26f).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH, MATCH))
        p.addView(rb, lin(dp(64), dp(64)))
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val r1 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        r1.addView(chip("−5") { adjust(-5) })
        tvTimer = tv("25:00", 28f, Color.WHITE, true).apply { setPadding(dp(8), 0, dp(8), 0) }
        r1.addView(tvTimer)
        r1.addView(chip("+5") { adjust(5) })
        r1.addView(chip("↻") { resetTimer() }, lin(WRAP, WRAP).apply { leftMargin = dp(6) })
        r.addView(r1)
        tvTMode = tv("Fokus", 12f, C_GRAY)
        r.addView(tvTMode)
        val r2 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        r2.addView(chip("☕ Rehat") { setTMode(1) })
        tvTPlay = tv("▶", 16f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(6), dp(18), dp(6))
            setOnClickListener { toggleTimer(); arm() }
        }
        r2.addView(tvTPlay, lin(WRAP, WRAP).apply { leftMargin = dp(4); rightMargin = dp(4) })
        r2.addView(chip("◎ Fokus") { setTMode(0) })
        r.addView(r2, lin(WRAP, WRAP).apply { topMargin = dp(6) })
        p.addView(r, lin(0, WRAP, 1f).apply { leftMargin = dp(10) })
        return p
    }

    private fun setTMode(m: Int) {
        tMode = m
        tTotal = (if (m == 0) sp.getInt("focusMin", 25) else sp.getInt("breakMin", 5)) * 60_000L
        tLeft = tTotal; tRun = false
        updTimerUi()
    }

    private fun toggleTimer() {
        if (tRun) { tLeft = tEnd - SystemClock.elapsedRealtime(); tRun = false }
        else { tEnd = SystemClock.elapsedRealtime() + tLeft; tRun = true }
        updTimerUi()
    }

    private fun adjust(minutes: Int) {
        if (tRun) return
        tTotal = (tTotal + minutes * 60_000L).coerceIn(60_000L, 180 * 60_000L)
        tLeft = tTotal
        updTimerUi()
    }

    private fun resetTimer() { tRun = false; tLeft = tTotal; updTimerUi() }

    private fun updTimerUi() {
        val left = if (tRun) maxOf(0L, tEnd - SystemClock.elapsedRealtime()) else tLeft
        tvTimer.text = fmtMs(left, true)
        tvTMode.text = if (tMode == 0) "Fokus" else "Istirahat"
        val col = if (tMode == 0) C_RED else C_GREEN
        ringT.set(if (tTotal > 0) left.toFloat() / tTotal else 0f, col)
        tvTPlay.text = if (tRun) "❚❚" else "▶"
        tvTPlay.background = rounded(col, dp(16))
    }

    private fun timerDone() {
        tRun = false
        val wasFocus = tMode == 0
        setTMode(if (wasFocus) 1 else 0)
        alertSound()
        showBanner("⏱", "Timer selesai", if (wasFocus) "Waktunya istirahat" else "Waktunya fokus lagi", C_RED, 1)
    }

    private fun alertSound() {
        if (!sp.getBoolean("remSound", true)) return
        try {
            getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } catch (_: Exception) {}
        try {
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play()
        } catch (_: Exception) {}
    }

    // ---------- halaman: chat WhatsApp ----------
    private fun mkChat(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        chatRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val h = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tvChatName = tv("", 15f, Color.WHITE, true)
        tvChatCount = tv("", 12f, C_GRAY)
        h.addView(tvChatName, lin(0, WRAP, 1f))
        h.addView(tvChatCount)
        h.addView(chip("‹") { chatIdx--; updChat() }, lin(WRAP, WRAP).apply { leftMargin = dp(8) })
        h.addView(chip("›") { chatIdx++; updChat() }, lin(WRAP, WRAP).apply { leftMargin = dp(4) })
        chatRow.addView(h)
        tvChatMsgs = tv("", 13f, Color.WHITE).apply {
            maxLines = 3
            background = rounded(C_DARK, dp(12))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        chatRow.addView(tvChatMsgs, lin(MATCH, WRAP).apply { topMargin = dp(6) })
        val b = LinearLayout(this)
        b.addView(chip("↩ Balas") { replyCur() })
        b.addView(chip("Buka WhatsApp") { openCur() }, lin(WRAP, WRAP).apply { leftMargin = dp(8) })
        chatRow.addView(b, lin(MATCH, WRAP).apply { topMargin = dp(8) })
        tvChatEmpty = tv("", 13f, C_GRAY).apply { gravity = Gravity.CENTER; maxLines = 3 }
        p.addView(chatRow, lin(MATCH, WRAP))
        p.addView(tvChatEmpty, lin(MATCH, WRAP))
        return p
    }

    private fun convs(): List<Conv> = ChatStore.convs.values.toList().asReversed()

    private fun updChat() {
        val l = convs()
        if (l.isEmpty()) {
            chatRow.visibility = View.GONE; tvChatEmpty.visibility = View.VISIBLE
            tvChatEmpty.text = if (ChatStore.connected) "Belum ada pesan WhatsApp.\nPesan baru akan muncul di sini."
            else "Izinkan akses notifikasi di Setelan\nagar chat WhatsApp muncul di sini."
            return
        }
        chatRow.visibility = View.VISIBLE; tvChatEmpty.visibility = View.GONE
        chatIdx = chatIdx.coerceIn(0, l.size - 1)
        val c = l[chatIdx]
        tvChatName.text = c.title
        tvChatCount.text = "${chatIdx + 1}/${l.size}"
        tvChatMsgs.text = c.msgs.takeLast(3).joinToString("\n") { if (it.sender == "Saya") "Saya: ${it.text}" else it.text }
    }

    private fun replyCur() {
        val c = convs().getOrNull(chatIdx) ?: return
        if (c.replyAction == null) {
            Toast.makeText(this, "Balasan cepat tidak tersedia, buka WhatsApp", Toast.LENGTH_SHORT).show()
            return
        }
        showInput("Balas ${c.title}…") { t ->
            if (ChatStore.sendReply(this, c, t)) {
                c.msgs.add(Msg("Saya", t, System.currentTimeMillis()))
                updChat()
            } else Toast.makeText(this, "Gagal mengirim", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCur() {
        try { convs().getOrNull(chatIdx)?.openIntent?.send() } catch (_: Exception) {}
        go(St.COMPACT)
    }

    private fun onNewChat(c: Conv) {
        chatIdx = 0
        updChat()
        if (!sp.getBoolean("chatPopup", true)) return
        val last = c.msgs.lastOrNull()
        showBanner(
            c.title.take(1).uppercase(), c.title,
            if (sp.getBoolean("chatPreview", true)) (last?.text ?: "") else "Pesan baru", C_WA, 2
        )
    }

    // ---------- halaman: kecerahan & suara ----------
    private fun mkBright(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val r1 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r1.addView(tv("☼", 24f, C_YEL).apply { gravity = Gravity.CENTER }, lin(dp(32), WRAP))
        briBar = BarView(this, C_YEL).apply { interactive = true; onChange = { f, _ -> setBrightness(f) } }
        r1.addView(briBar, lin(0, dp(28), 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        tvBri = tv("0%", 13f, Color.WHITE, true).apply { gravity = Gravity.END }
        r1.addView(tvBri, lin(dp(42), WRAP))
        p.addView(r1)
        val r2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r2.addView(tv("🔊", 20f).apply { gravity = Gravity.CENTER }, lin(dp(32), WRAP))
        volBar = BarView(this, C_PINK).apply {
            interactive = true
            onChange = { f, _ ->
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (f * mxVol()).roundToInt(), 0)
                tvVol.text = "${(f * 100).roundToInt()}%"
            }
        }
        r2.addView(volBar, lin(0, dp(28), 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        tvVol = tv("0%", 13f, Color.WHITE, true).apply { gravity = Gravity.END }
        r2.addView(tvVol, lin(dp(42), WRAP))
        p.addView(r2, lin(MATCH, WRAP).apply { topMargin = dp(8) })
        btnPerm = chip("Izinkan ubah kecerahan") { openWriteSettings() }
        btnPerm.visibility = View.GONE
        p.addView(btnPerm, lin(WRAP, WRAP).apply { topMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL })
        return p
    }

    private fun mxVol() = maxOf(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    private fun readBri(): Int = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 128 }

    private fun setBrightness(f: Float) {
        if (!Settings.System.canWrite(this)) { btnPerm.visibility = View.VISIBLE; return }
        val v = (1 + f * 254).roundToInt().coerceIn(1, 255)
        try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
            lastBri = v
        } catch (_: Exception) {}
        tvBri.text = "${(f * 100).roundToInt()}%"
    }

    private fun syncSliders() {
        btnPerm.visibility = if (Settings.System.canWrite(this)) View.GONE else View.VISIBLE
        if (!briBar.touching) {
            val f = ((readBri() - 1) / 254f).coerceIn(0f, 1f)
            briBar.value = f; tvBri.text = "${(f * 100).roundToInt()}%"
        }
        if (!volBar.touching) {
            val f = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / mxVol()
            volBar.value = f; tvVol.text = "${(f * 100).roundToInt()}%"
        }
    }

    private fun showHud(icon: String, f: Float, color: Int) {
        hudIcon.text = icon; hudBar.fill = color; hudBar.value = f
        hudPct.text = "${(f * 100).roundToInt()}%"
        if (st != St.PANEL) go(St.HUD)
    }

    private fun pollAudio() {
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (lastVol >= 0 && v != lastVol) {
            val f = v.toFloat() / mxVol()
            if (sp.getBoolean("hudVol", true)) showHud(if (v == 0) "🔇" else if (f < 0.5f) "🔉" else "🔊", f, C_PINK)
        }
        lastVol = v
        val b = readBri()
        if (lastBri >= 0 && abs(b - lastBri) > 1) {
            if (sp.getBoolean("hudBri", true)) showHud("☀️", ((b - 1) / 254f).coerceIn(0f, 1f), C_YEL)
        }
        lastBri = b
    }

    // ---------- halaman: to-do ----------
    private fun mkTodo(): LinearLayout {
        val p = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvTodoCnt = tv("0/0", 28f, Color.WHITE, true)
        l.addView(tvTodoCnt)
        l.addView(tv("Tugas selesai", 12f, C_GRAY))
        l.addView(chip("＋ Tambah") { addTodo() }, lin(WRAP, WRAP).apply { topMargin = dp(8) })
        l.addView(chip("Hapus selesai") { clearDone() }, lin(WRAP, WRAP).apply { topMargin = dp(6) })
        p.addView(l, lin(dp(108), WRAP))
        val sv = ScrollView(this).apply { background = rounded(C_DARK, dp(14)); isVerticalScrollBarEnabled = false }
        todoList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(6)) }
        sv.addView(todoList)
        p.addView(sv, lin(0, MATCH, 1f).apply { leftMargin = dp(10); topMargin = dp(4); bottomMargin = dp(4) })
        return p
    }

    private fun updTodo() {
        val l = Todos.load(sp)
        tvTodoCnt.text = "${l.count { it.d }}/${l.size}"
        todoList.removeAllViews()
        if (l.isEmpty()) todoList.addView(tv("Belum ada tugas", 12f, C_GRAY).apply { setPadding(0, dp(6), 0, 0) })
        l.forEach { t ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, dp(5)) }
            row.addView(tv(if (t.d) "●" else "○", 16f, if (t.d) C_GREEN else C_GRAY))
            row.addView(tv(t.t, 13f, if (t.d) C_GRAY else Color.WHITE).apply {
                setPadding(dp(8), 0, 0, 0)
                if (t.d) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }, lin(0, WRAP, 1f))
            row.setOnClickListener { t.d = !t.d; Todos.save(sp, l); updTodo(); arm() }
            todoList.addView(row)
        }
    }

    private fun addTodo() {
        showInput("Tugas baru…") { s ->
            val l = Todos.load(sp); l.add(Todo(s, false)); Todos.save(sp, l); updTodo()
        }
    }

    private fun clearDone() {
        val l = Todos.load(sp); l.removeAll { it.d }; Todos.save(sp, l); updTodo()
    }

    // ---------- input teks di overlay ----------
    private fun showInput(hint: String, cb: (String) -> Unit) {
        inputCb = cb
        edit.hint = hint; edit.setText("")
        tabBar.visibility = View.GONE; inputBar.visibility = View.VISIBLE
        ui.removeCallbacks(autoBack)
        outer?.let {
            wp.flags = wp.flags and LP.FLAG_NOT_FOCUSABLE.inv()
            wp.softInputMode = LP.SOFT_INPUT_ADJUST_PAN
            wm.updateViewLayout(it, wp)
        }
        edit.requestFocus()
        ui.postDelayed({
            getSystemService(InputMethodManager::class.java).showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun hideInput() {
        if (!inputOn()) return
        try { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(edit.windowToken, 0) } catch (_: Exception) {}
        inputBar.visibility = View.GONE; tabBar.visibility = View.VISIBLE
        outer?.let {
            wp.flags = wp.flags or LP.FLAG_NOT_FOCUSABLE
            wm.updateViewLayout(it, wp)
        }
        inputCb = null
    }

    private fun submitInput() {
        val t = edit.text.toString().trim()
        val cb = inputCb
        hideInput()
        if (t.isNotEmpty()) cb?.invoke(t)
        arm()
    }

    // ---------- navigasi & animasi ----------
    private fun selectPage(i: Int) {
        pageIdx = i
        pages.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { k, t -> t.setTextColor(if (k == i) pageColors[k] else C_GRAY) }
        val h = heads[i]
        tvHead.text = SpannableString(h.first + h.second).apply {
            setSpan(ForegroundColorSpan(pageColors[i]), h.first.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        refreshPage()
        arm()
    }

    private fun openPage(i: Int) { selectPage(i); go(St.PANEL) }

    private fun refreshPage() {
        when (pageIdx) {
            1 -> updTimerUi()
            2 -> updChat()
            3 -> syncSliders()
            4 -> updTodo()
            else -> {}
        }
    }

    private fun showBanner(av: String, title: String, text: String, color: Int, page: Int) {
        if (st == St.PANEL) return
        banAv.text = av; banAv.background = oval(color)
        banTitle.text = title; banText.text = text
        bannerPage = page
        go(St.BANNER)
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        go(St.COMPACT)
    }

    private fun openWriteSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        go(St.COMPACT)
    }

    private fun go(n: St) {
        ui.removeCallbacks(autoBack)
        if (n != St.COMPACT && !inputOn()) {
            ui.postDelayed(autoBack, when (n) { St.PANEL -> 8000L; St.HUD -> 1800L; else -> 5000L })
        }
        if (st == n) return
        if (n != St.PANEL) hideInput()
        st = n
        val target: View = when (n) { St.COMPACT -> small; St.PANEL -> big; St.HUD -> hud; St.BANNER -> banner }
        small.visibility = if (n == St.COMPACT) View.VISIBLE else View.GONE
        big.visibility = if (n == St.PANEL) View.VISIBLE else View.GONE
        hud.visibility = if (n == St.HUD) View.VISIBLE else View.GONE
        banner.visibility = if (n == St.BANNER) View.VISIBLE else View.GONE
        target.alpha = 0f
        if (n == St.PANEL) refreshPage()
        val (w1, h1) = when (n) {
            St.COMPACT -> Pair(compW, compH)
            St.PANEL -> Pair(expW, expH)
            St.HUD -> Pair(hudW, hudH)
            St.BANNER -> Pair(banW, banH)
        }
        val b1 = if (n == St.PANEL) dp(30).toFloat() else h1 / 2f
        val t1 = if (topFlat) 0f else b1
        val w0 = pill.layoutParams.width; val h0 = pill.layoutParams.height
        val t0 = curT; val b0 = curB
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                pill.layoutParams = pill.layoutParams.apply {
                    width = (w0 + (w1 - w0) * t).toInt()
                    height = (h0 + (h1 - h0) * t).toInt()
                }
                setRad(t0 + (t1 - t0) * t, b0 + (b1 - b0) * t)
                target.alpha = t.coerceIn(0f, 1f)
            }
            start()
        }
    }

    // ---------- detak tiap 300 ms ----------
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val hh = sp.getBoolean("h24", false)
            if (hh != h24) { h24 = hh; fmtT = SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Locale.getDefault()) }
            val pct = try { getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (e: Exception) { 100 }
            val bc = if (pct <= 15) C_RED else if (pct <= 30) C_ORG else C_GREEN
            ringS.set(pct / 100f, bc); ringB.set(pct / 100f, bc)
            if (tRun) {
                tLeft = tEnd - SystemClock.elapsedRealtime()
                if (tLeft <= 0) timerDone()
            }
            if (tRun) {
                tvTimeS.text = fmtMs(tLeft, true)
                tvTimeS.setTextColor(if (tMode == 0) C_RED else C_GREEN)
            } else {
                tvTimeS.text = fmtT.format(now)
                tvTimeS.setTextColor(Color.WHITE)
            }
            if (st == St.PANEL) {
                tvDate.text = if (sp.getBoolean("showDate", true)) fmtD.format(now) + "  " + fmtT.format(now) else fmtT.format(now)
                when (pageIdx) {
                    0 -> updProgress()
                    1 -> updTimerUi()
                    3 -> syncSliders()
                    else -> {}
                }
            }
            lyricTick()
            pollAudio()
            ui.postDelayed(this, 300)
        }
    }

    // ---------- musik: sesi media + lirik ----------
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
        curDur = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        tvTitle.text = title ?: "Tidak ada musik"
        tvArtist.text = artist
        artB.setImageBitmap(art)
        tvNote.visibility = if (art == null) View.VISIBLE else View.GONE
        if (!notch) tvMid.text = title ?: ""
        if (title != null && sp.getBoolean("lyrics", true)) loadLyrics(title, artist)
        else { lyricKey = ""; lyrics = emptyList(); lastLine = ""; tvLyric.text = "" }
    }

    private fun refreshState() {
        tvPlay.text = if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) "❚❚" else "▶"
    }

    private fun curPos(): Long {
        val s = controller?.playbackState ?: return 0L
        val p = if (s.state == PlaybackState.STATE_PLAYING)
            s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
        else s.position
        return maxOf(0L, p)
    }

    private fun updProgress() {
        if (controller?.playbackState == null) return
        val pos = curPos()
        if (curDur > 0) {
            if (!seekBar.touching) seekBar.value = pos.toFloat() / curDur
            tvPos.text = fmtMs(pos)
            tvDur.text = "-" + fmtMs(maxOf(0L, curDur - pos))
        }
    }

    private fun lyricTick() {
        if (!lyricShow || lyrics.isEmpty()) return
        val pos = curPos()
        val line = lyrics.lastOrNull { it.first <= pos }?.second ?: ""
        if (line != lastLine) { lastLine = line; tvLyric.text = line.ifBlank { "♪" } }
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
                c.setRequestProperty("User-Agent", "DynIsland/2.0")
                c.connectTimeout = 8000; c.readTimeout = 8000
                val arr = JSONArray(c.inputStream.bufferedReader().readText())
                var found: String? = null
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i).optString("syncedLyrics")
                    if (s.isNotBlank() && s != "null") { found = s; break }
                }
                if (found != null) parseLrc(found) else emptyList()
            } catch (e: Exception) { emptyList() }
            ui.post {
                if (lyricKey == key) {
                    lyrics = res
                    if (res.isEmpty()) tvLyric.text = "Lirik tidak ditemukan"
                }
            }
        }.start()
    }

    private fun teardown() {
        ui.removeCallbacksAndMessages(null)
        anim?.cancel()
        ChatStore.listener = null
        controller?.unregisterCallback(cb); controller = null
        try { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        outer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        outer = null; st = St.COMPACT; lyricKey = ""; lyrics = emptyList(); tRun = false
    }
}
