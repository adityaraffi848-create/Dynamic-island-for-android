package com.example.dynisland

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class Msg(val sender: String, val text: String, val time: Long)

class Conv(val key: String, val title: String) {
    val msgs = ArrayList<Msg>()
    var replyAction: Notification.Action? = null
    var openIntent: PendingIntent? = null
}

/** Penyimpanan chat WhatsApp di memori saja (tidak ditulis ke disk). */
object ChatStore {
    val convs = LinkedHashMap<String, Conv>()
    @Volatile var listener: ((Conv) -> Unit)? = null
    @Volatile var connected = false

    fun sendReply(ctx: Context, c: Conv, text: String): Boolean {
        val a = c.replyAction ?: return false
        val ri = a.remoteInputs ?: return false
        val i = Intent()
        val b = Bundle()
        for (r in ri) b.putCharSequence(r.resultKey, text)
        RemoteInput.addResultsToIntent(ri, i, b)
        return try { a.actionIntent.send(ctx, 0, i); true } catch (e: Exception) { false }
    }
}

// Izin "akses notifikasi" untuk service ini membuka akses ke sesi media (Spotify dll)
// sekaligus notifikasi WhatsApp.
class MediaListener : NotificationListenerService() {
    private val wa = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onListenerConnected() { ChatStore.connected = true }
    override fun onListenerDisconnected() { ChatStore.connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in wa) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (title.equals("WhatsApp", true)) return

        val key = sbn.packageName + "|" + title
        val c = ChatStore.convs.remove(key) ?: Conv(key, title)
        ChatStore.convs[key] = c   // taruh di akhir = paling baru
        val last = c.msgs.lastOrNull()
        if (last == null || last.text != text || kotlin.math.abs(last.time - sbn.postTime) > 1500) {
            c.msgs.add(Msg(title, text, sbn.postTime))
            if (c.msgs.size > 30) c.msgs.removeAt(0)
        }
        val act = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        if (act != null) c.replyAction = act
        c.openIntent = n.contentIntent
        ChatStore.listener?.invoke(c)
    }
}
