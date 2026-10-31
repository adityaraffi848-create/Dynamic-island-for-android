package com.example.dynisland

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

        val sp = getSharedPreferences("p", MODE_PRIVATE)
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }
        fun restartIfRunning() {
            if (IslandService.running) startForegroundService(Intent(this, IslandService::class.java))
        }
        fun btn(t: String, a: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { a() }
        }.also { col.addView(it) }

        col.addView(TextView(this).apply { text = "Dynamic Island"; textSize = 24f })

        btn("1. Izinkan tampil di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2. Izinkan akses notifikasi (untuk baca musik)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val rbNotch = RadioButton(this).apply { id = View.generateViewId(); text = "Mode Poni (nempel di kamera)" }
        val rbFloat = RadioButton(this).apply { id = View.generateViewId(); text = "Mode Floating (bisa digeser)" }
        col.addView(RadioGroup(this).apply {
            addView(rbNotch); addView(rbFloat)
            check(if (sp.getString("mode", "notch") == "notch") rbNotch.id else rbFloat.id)
            setOnCheckedChangeListener { _, id ->
                sp.edit().putString("mode", if (id == rbNotch.id) "notch" else "float").apply()
                restartIfRunning()
            }
        })

        fun seek(label: String, max: Int, cur: Int, onSet: (Int) -> Unit) {
            col.addView(TextView(this).apply { text = label })
            col.addView(SeekBar(this).apply {
                this.max = max; progress = cur
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {}
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) { onSet(s!!.progress); restartIfRunning() }
                })
            })
        }
        seek("Jarak dari atas layar (mode poni)", 60, sp.getInt("oy", 4)) { sp.edit().putInt("oy", it).apply() }
        seek("Lebar poni (sesuaikan dengan kamera)", 140, sp.getInt("w", 150) - 80) { sp.edit().putInt("w", it + 80).apply() }

        btn("▶ Mulai") {
            if (!Settings.canDrawOverlays(this)) Toast.makeText(this, "Izinkan overlay dulu (langkah 1)", Toast.LENGTH_LONG).show()
            else startForegroundService(Intent(this, IslandService::class.java))
        }
        btn("■ Stop") { stopService(Intent(this, IslandService::class.java)) }

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
