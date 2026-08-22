import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

# Replace these flow definitions which reference SyncEngine directly
bad_str = """    val isOnline: StateFlow<Boolean> = repository.syncEngine?.isOnline ?: MutableStateFlow(true)
    val syncing: StateFlow<Boolean> = repository.syncEngine?.syncing ?: MutableStateFlow(false)
    val lastSyncTime: StateFlow<Long> = repository.syncEngine?.lastSyncTime ?: MutableStateFlow(0L)
    val pendingChangesCount: StateFlow<Int> = repository.syncEngine?.pendingChangesCount ?: MutableStateFlow(0)"""

good_str = """    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()
    private val _syncingFlow = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncingFlow.asStateFlow()
    private val _lastSyncTimeFlow = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTimeFlow.asStateFlow()
    private val _pendingChangesCountFlow = MutableStateFlow(0)
    val pendingChangesCount: StateFlow<Int> = _pendingChangesCountFlow.asStateFlow()"""

content = content.replace(bad_str, good_str)
content = content.replace("repository.syncEngine?.setOnline(online)", "_isOnlineFlow.value = online")

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)

