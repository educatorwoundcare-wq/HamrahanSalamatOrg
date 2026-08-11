package com.example.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncLifecycleObserver(
    private val syncManager: OfflineFirstSyncManager,
    private val workspaceId: String,
    private val deviceId: String
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Hybrid Trigger 2: Sync on Resume
        CoroutineScope(Dispatchers.IO).launch {
            syncManager.performDeltaSync(workspaceId, deviceId)
        }
    }
}
