package com.example.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Hybrid Trigger 1: FCM Data Message Wakeup
        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            if (action == "SYNC_REQUIRED") {
                Log.i("FCMService", "Received SYNC_REQUIRED push. Triggering background sync...")
                
                // Trigger background sync
                // Assuming we can resolve dependencies here (e.g., via DI / Hilt or manual singleton)
                // val syncManager = resolveSyncManager()
                // val workspaceId = getWorkspaceId()
                // val deviceId = getDeviceId()
                // CoroutineScope(Dispatchers.IO).launch {
                //     syncManager.performDeltaSync(workspaceId, deviceId)
                // }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("FCMService", "Refreshed token: $token")
        // Send the new token to your server to keep push notifications working
    }
}
