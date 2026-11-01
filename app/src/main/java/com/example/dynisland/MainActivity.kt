package com.example.dynisland

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Menu setelan: sidebar kategori di kiri, isi di kanan (mirip menu di app notch desktop). */
class MainActivity : Activity() {
    private class Sec(val name: String, val glyph: String, val color: Int)

    private lateinit var sp: SharedPreferences
    private lateinit var side: LinearLayout
    private lateinit var content: LinearLayout
    private var sel = 0

    private val BG = 0xFF1C1C1E.toInt()
    private val CARD = 0xFF2C2C2E.toInt()
    private val SUB = 0xFF8E8E93.toInt()
    private val GREEN = 0xFF30D158.toInt()
    private val RED = 0xFFFF453A.toInt()
    private val secs = listOf(
        Sec("Umum", "⚙\uFE0E", 0xFF8E8E93.toInt()),
        Sec("Tampilan", "◐", 0xFF5E5CE6.toInt()),
        Sec("Musik", "♪", 0xFFFF375F.toInt()),
        Sec("Chat", "✉\uFE0E", 0xFF25D366.toInt()),
        Sec("Timer", "⏱\uFE0E", 0xFFFF9F0A.toInt()),
        Sec("To-do", "✔\uFE0E", 0xFF30D158.toInt()),
        Sec("Cahaya", "☼", 0xFFFFD60A.toInt()),
        Sec("Tentang", "ⓘ", 0xFF636366.toInt())
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = r.toFloat() }
    private fun txt(t: String, size: Float, color: Int) = TextView(this).apply { text = t; textSize = size; setTextColor(color) }
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("p", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply { setBackgroundColor(BG) }
        side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(14), dp(6), dp(14)) }
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(side) }, lp(dp(82), MATCH))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(18), dp(14), dp(28)) }
        root.addView(ScrollView(this).apply { addView(content) }, lp(0, MATCH, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun restartIfRunning() {
        if (IslandService.running) startForegroundService(Intent(this, IslandService::class.java))
    }

    // ---------- kerangka ----------
    private fun render() {
        side.removeAllViews(); content.removeAllViews()
        secs.forEachIndexed { i, s ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(8), dp(2), dp(8))
                if (i == sel) background = rounded(CARD, dp(12))
                setOnClickListener { sel = i; render() }
            }
            val ic = txt(s.glyph, 17f, Color.WHITE).apply { gravity = Gravity.CENTER; background = rounded(s.color, dp(9)) }
            item.addView(ic, lp(dp(34), dp(34)))
            item.addView(txt(s.name, 10f, Color.WHITE).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
            side.addView(item)
        }
        content.addView(txt(secs[sel].name, 24f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, dp(6)) })
        when (sel) {
            0 -> secGeneral()
            1 -> secDisplay()
            2 -> secMusic()
            3 -> secChat()
            4 -> secTimer()
            5 -> secTodo()
            6 -> secLight()
            else -> secAbout()
        }
    }

    private fun group(label: String? = null): LinearLayout {
        if (label != null) content.addView(txt(label, 12f, SUB).apply { setPadding(dp(8), dp(14), 0, dp(6)) })
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(CARD, dp(12)) }
        content.addView(card, lp(MATCH, WRAP))
        return card
    }

    private fun addRow(card: LinearLayout, v: View) {
        if (card.childCount > 0) {
            card.addView(View(this).apply { setBackgroundColor(0xFF3A3A3C.toInt()) }, lp(MATCH, 1).apply { leftMargin = dp(14) })
        }
        card.addView(v)
    }

    private fun info(t: String) {
        content.addView(txt(t, 12f, SUB).apply { setPadding(dp(8), dp(10), dp(8), 0) })
    }

    private fun mkSwitch(on: Boolean, cb: (Boolean) -> Unit) = Switch(this).apply {
        isChecked = on
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(GREEN, 0xFF48484A.toInt()))
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        setOnCheckedChangeListener { _, c -> cb(c) }
    }

    private fun titleCol(title: String, sub: String?): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(txt(title, 15f, Color.WHITE))
        if (sub != null) c.addView(txt(sub, 12f, SUB))
        return c
    }

    private fun toggle(card: LinearLayout, title: String, sub: String?, on: Boolean, cb: (Boolean) -> Unit) {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        r.addView(titleCol(title, sub), lp(0, WRAP, 1f))
        r.addView(mkSwitch(on, cb))
        addRow(card, r)
    }

    private fun pref(card: LinearLayout, title: String, sub: String?, key: String, def: Boolean, restart: Boolean = false) {
        toggle(card, title, sub, sp.getBoolean(key, def)) {
            sp.edit().putBoolean(key, it).apply()
            if (restart) restartIfRunning()
        }
    }

    private fun smallBtn(t: String, color: Int = GREEN, a: () -> Unit) = txt(t, 13f, Color.WHITE).apply {
        gravity = Gravity.CENTER
        background = rounded(color, dp(14))
        setPadding(dp(14), dp(7), dp(14), dp(7))
        setOnClickListener { a() }
    }

    private fun action(card: LinearLayout, title: String, sub: String?, label: String, color: Int = GREEN, a: () -> Unit) {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        r.addView(titleCol(title, sub), lp(0, WRAP, 1f))
        r.addView(smallBtn(label, color, a))
        addRow(card, r)
    }

    private fun seg(card: LinearLayout, title: String, sub: String?, labels: List<String>, cur: Int, pick: (Int) -> Unit) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        r.addView(titleCol(title, sub))
        val bar = LinearLayout(this).apply { background = rounded(BG, dp(8)); setPadding(dp(2), dp(2), dp(2), dp(2)) }
        val tvs = ArrayList<TextView>()
        fun paint(s: Int) {
            tvs.forEachIndexed { i, t -> t.background = if (i == s) rounded(0xFF636366.toInt(), dp(7)) else null }
        }
        labels.forEachIndexed { i, l ->
            val t = txt(l, 13f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
                setOnClickListener { paint(i); pick(i) }
            }
            tvs.add(t)
            bar.addView(t, lp(0, WRAP, 1f))
        }
        paint(cur)
        r.addView(bar, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        addRow(card, r)
    }

    private fun slider(card: LinearLayout, title: String, min: Int, max: Int, cur: Int, unit: String, onFinal: (Int) -> Unit) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val h = LinearLayout(this)
        h.addView(txt(title, 15f, Color.WHITE), lp(0, WRAP, 1f))
        val vt = txt("$cur$unit", 14f, SUB)
        h.addView(vt)
        r.addView(h)
        val bar = BarView(this, GREEN).apply {
            interactive = true
            value = (cur - min).toFloat() / (max - min)
        }
        bar.onChange = { f, fin ->
            val v = (min + f * (max - min)).roundToInt()
            vt.text = "$v$unit"
            if (fin) onFinal(v)
        }
        r.addView(bar, lp(MATCH, dp(26)).apply { topMargin = dp(6) })
        addRow(card, r)
    }

    private fun permRow(card: LinearLayout, title: String, ok: Boolean, act: () -> Unit) {
        action(card, title, if (ok) "Aktif ✓" else "Belum diizinkan", if (ok) "Buka" else "Izinkan", if (ok) 0xFF48484A.toInt() else GREEN, act)
    }

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun openOverlay() = startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    private fun openListener() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    private fun openWrite() = startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))

    // ---------- bagian-bagian ----------
    private fun secGeneral() {
        val svc = group("Layanan")
        toggle(svc, "Aktifkan Dynamic Island", "Tampilkan island di layar", IslandService.running) { on ->
            if (on) {
                if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, IslandService::class.java))
                else { Toast.makeText(this, "Izinkan tampil di atas aplikasi lain dulu", Toast.LENGTH_LONG).show(); render() }
            } else stopService(Intent(this, IslandService::class.java))
        }
        val perm = group("Izin")
        permRow(perm, "Tampil di atas aplikasi lain", Settings.canDrawOverlays(this)) { openOverlay() }
        permRow(perm, "Akses notifikasi (musik & WhatsApp)", listenerOn()) { openListener() }
        permRow(perm, "Ubah kecerahan layar", Settings.System.canWrite(this)) { openWrite() }

        val nt = group("Notch")
        seg(nt, "Mode", "Poni menempel di kamera, Floating bisa digeser",
            listOf("Poni", "Floating"), if (sp.getString("mode", "notch") == "notch") 0 else 1) {
            sp.edit().putString("mode", if (it == 0) "notch" else "float").apply(); restartIfRunning()
        }
        val scales = listOf(100, 115, 130, 150)
        seg(nt, "Ukuran", "Island dimulai ulang untuk menerapkan ukuran baru",
            scales.map { "$it%" }, scales.indexOf(sp.getInt("scale", 100)).coerceAtLeast(0)) {
            sp.edit().putInt("scale", scales[it]).apply(); restartIfRunning()
        }
        pref(nt, "Jalankan saat HP menyala", "Island aktif otomatis setelah restart", "boot", false)

        val snd = group("Suara")
        pref(snd, "Suara & getar pengingat", "Saat timer selesai", "remSound", true)
    }

    private fun secDisplay() {
        val g = group("Posisi & ukuran")
        slider(g, "Jarak dari atas (mode poni)", 0, 60, sp.getInt("oy", 0), " dp") { sp.edit().putInt("oy", it).apply(); restartIfRunning() }
        slider(g, "Lebar poni", 80, 220, sp.getInt("w", 150), " dp") { sp.edit().putInt("w", it).apply(); restartIfRunning() }
        val g2 = group("Jam")
        pref(g2, "Format 24 jam", null, "h24", false)
        pref(g2, "Tampilkan tanggal di panel", null, "showDate", true)
        info("Atur jarak dan lebar sampai pill pas membungkus kamera depan.")
    }

    private fun secMusic() {
        val g = group("Lirik")
        pref(g, "Tampilkan lirik", "Lirik tersinkron diambil dari lrclib.net", "lyrics", true)
        info("Musik dibaca dari pemutar yang sedang aktif (Spotify, YouTube Music, dll). Butuh izin akses notifikasi. Tombol ❝ di panel menyembunyikan lirik.")
    }

    private fun secChat() {
        val g = group("WhatsApp")
        pref(g, "Pop-up pesan baru", "Island melebar saat ada pesan masuk", "chatPopup", true)
        pref(g, "Tampilkan isi pesan di pop-up", "Matikan untuk hanya menampilkan \"Pesan baru\"", "chatPreview", true)
        val p = group("Izin")
        permRow(p, "Akses notifikasi", listenerOn()) { openListener() }
        info("Island membaca notifikasi WhatsApp & WhatsApp Business. Pesan hanya disimpan di memori selama island aktif. Balas lewat tombol Balas di panel Chat.")
    }

    private fun secTimer() {
        val g = group("Durasi")
        slider(g, "Fokus", 1, 60, sp.getInt("focusMin", 25), " mnt") { sp.edit().putInt("focusMin", it).apply() }
        slider(g, "Istirahat", 1, 30, sp.getInt("breakMin", 5), " mnt") { sp.edit().putInt("breakMin", it).apply() }
        info("Berlaku saat memilih mode Fokus / Rehat di panel. Di panel Timer, tombol −5 dan +5 mengubah durasi secara manual.")
    }

    private fun secTodo() {
        val g = group("Tambah tugas")
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(6), dp(10), dp(6)) }
        val et = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(SUB); hint = "Tulis tugas…"; isSingleLine = true; textSize = 15f
        }
        r.addView(et, lp(0, WRAP, 1f))
        r.addView(smallBtn("Tambah") {
            val t = et.text.toString().trim()
            if (t.isNotEmpty()) {
                val l = Todos.load(sp); l.add(Todo(t, false)); Todos.save(sp, l); render()
            }
        })
        addRow(g, r)
        val l = Todos.load(sp)
        val g2 = group("Daftar")
        action(g2, "Hapus tugas selesai", "${l.count { it.d }} dari ${l.size} tugas selesai", "Hapus", RED) {
            val x = Todos.load(sp); x.removeAll { it.d }; Todos.save(sp, x); render()
        }
        action(g2, "Hapus semua tugas", null, "Hapus", RED) { Todos.save(sp, emptyList()); render() }
        info("Tugas juga bisa ditambah dan dicentang langsung dari panel To-do di island.")
    }

    private fun secLight() {
        val g = group("HUD")
        pref(g, "HUD volume", "Island menampilkan volume saat tombol volume ditekan", "hudVol", true)
        pref(g, "HUD kecerahan", "Island menampilkan kecerahan saat berubah", "hudBri", true)
        val p = group("Izin")
        permRow(p, "Ubah kecerahan layar", Settings.System.canWrite(this)) { openWrite() }
        info("Slider kecerahan dan volume ada di panel Kecerahan & Suara. Mengubah kecerahan lewat island mematikan kecerahan otomatis.")
    }

    private fun secAbout() {
        val g = group()
        addRow(g, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(txt("Dynamic Island untuk Android", 16f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(txt("Versi 2.0 · musik, lirik, timer, chat WhatsApp, kecerahan, volume, to-do", 12f, SUB))
        })
    }
}
