with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('importTariffs(uri)', 'importTariffs(uri, context)')

with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'w') as f:
    f.write(content)
