import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    text = f.read()

text = text.replace('(context.applicationContext as? com.example.HamrahanApplication)?.repository', '(context.applicationContext as? com.example.HamrahanApplication)?.container?.repository')

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(text)
