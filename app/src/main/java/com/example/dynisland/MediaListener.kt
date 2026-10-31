package com.example.dynisland

import android.service.notification.NotificationListenerService

// Hanya perlu ada: izin "akses notifikasi" ini yang membuka akses ke sesi media (Spotify dll).
class MediaListener : NotificationListenerService()
