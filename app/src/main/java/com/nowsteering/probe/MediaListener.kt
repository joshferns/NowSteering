package com.nowsteering.probe

import android.service.notification.NotificationListenerService

/** Notification-listener access is what lets us list other apps' media sessions. */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        ProbeLog.log("LISTENER", "notification listener connected")
        ProbeApp.watcher.start()
    }

    override fun onListenerDisconnected() {
        ProbeLog.log("LISTENER", "notification listener disconnected")
    }
}
