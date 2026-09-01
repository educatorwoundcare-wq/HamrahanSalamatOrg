import re
with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    text = f.read()

text = text.replace('version = 23,', 'version = 24,')

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(text)
