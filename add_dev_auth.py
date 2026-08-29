import re
with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

auth_logic = """
    // --- Secure Master Password Auth ---
    private var devSessionValidUntil: Long = 0L
    private var devAuthFailedAttempts: Int = 0
    private var devAuthLockoutUntil: Long = 0L

    val devAuthLockoutRemaining: kotlinx.coroutines.flow.StateFlow<Long> = kotlinx.coroutines.flow.flow {
        while (true) {
            val remaining = devAuthLockoutUntil - System.currentTimeMillis()
            emit(if (remaining > 0) remaining else 0L)
            kotlinx.coroutines.delay(1000)
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0L)

    fun isDevSessionValid(): Boolean {
        return System.currentTimeMillis() < devSessionValidUntil
    }

    fun keepDevSessionAlive() {
        if (isDevSessionValid()) {
            devSessionValidUntil = System.currentTimeMillis() + 5 * 60 * 1000L
        }
    }

    fun verifyDevPin(pin: String): Boolean {
        if (System.currentTimeMillis() < devAuthLockoutUntil) {
            logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Attempted while locked out")
            return false
        }
        
        // Normalize Persian digits to English
        val englishPin = pin
            .replace("۰", "0")
            .replace("۱", "1")
            .replace("۲", "2")
            .replace("۳", "3")
            .replace("۴", "4")
            .replace("۵", "5")
            .replace("۶", "6")
            .replace("۷", "7")
            .replace("۸", "8")
            .replace("۹", "9")

        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(englishPin.toByteArray())
            .joinToString("") { "%02x".format(it) }

        if (hash == "1e3803e3f3e1f286d8945c5e052b8f7a6e304416fe2d3e5f7be0dafed8a0ecf7") {
            devAuthFailedAttempts = 0
            devSessionValidUntil = System.currentTimeMillis() + 5 * 60 * 1000L
            logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SUCCESS", "Authentication successful")
            return true
        } else {
            devAuthFailedAttempts++
            if (devAuthFailedAttempts >= 3) {
                devAuthLockoutUntil = System.currentTimeMillis() + 30_000L
                logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Lockout triggered due to 3 failed attempts")
            } else {
                logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_FAILURE", "Authentication failed")
            }
            return false
        }
    }
    
    fun notifyDevAuthScreenOpened() {
        logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SCREEN_OPENED", "Developer Authentication Screen Opened")
    }
    
    fun notifyDevSessionExpired() {
        logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_SESSION_EXPIRED", "Developer Session Expired")
    }
"""

idx = content.find("fun updateCurrentUserRole")
content = content[:idx] + auth_logic + "\n" + content[idx:]

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)

