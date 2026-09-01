import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    text = f.read()

text = text.replace('confirmedWorkspace.syncCode', 'confirmedWorkspace.companySyncCode')
text = text.replace('context.applicationContext as? com.example.HamrahanApplication', 'context.applicationContext as? com.example.HamrahanApplication')

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(text)
