import os
import re

auth_dir = 'app/src/main/java/com/example/data/auth'

def clean_file(path):
    with open(path, 'r') as f:
        c = f.read()
    
    # Remove the dummy FirebaseException class if present
    c = re.sub(r'open class FirebaseException\(message: String\) : Exception\(message\) \{.*?\n\}\n', '', c, flags=re.DOTALL)
    
    # Revert all those stupid replacements from my first script!
    # Wait, my first script did: Exception.AuthenticationError -> FirebaseException.AuthenticationError
    # We can just define FirebaseException properly, but we need to match the constructor args!
    # Let's just restore the file from before I touched them! Wait, I don't have git.
    
    with open(path, 'w') as f:
        f.write(c)

for file in os.listdir(auth_dir):
    if file.endswith('.kt'):
        clean_file(os.path.join(auth_dir, file))

# Create FirebaseException.kt
fb_ex_code = """package com.example.data.auth

open class FirebaseException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationError(val code: Any?, val reason: String?, msg: String) : FirebaseException(msg)
    class UnknownError(val code: Any?, msg: String, cause: Throwable? = null) : FirebaseException(msg, cause)
    class TokenExpired(val code: Any?, msg: String) : FirebaseException(msg)
    class PermissionDenied(val code: Any?, msg: String) : FirebaseException(msg)
    class WorkspaceNotFound(val code: Any?, msg: String) : FirebaseException(msg)
    class NetworkError(val code: Any?, msg: String, cause: Throwable? = null) : FirebaseException(msg, cause)
    class InvalidResponse(msg: String) : FirebaseException(msg)
}
"""
with open(os.path.join(auth_dir, 'FirebaseException.kt'), 'w') as f:
    f.write(fb_ex_code)
