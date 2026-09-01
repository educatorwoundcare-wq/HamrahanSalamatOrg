import re

with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'r') as f:
    text = f.read()

start_idx = text.find('    if (shouldShowDialog) {')
end_idx = text.find('    DisposableEffect(Unit) {')

if start_idx != -1 and end_idx != -1:
    text = text[:start_idx] + text[end_idx:]
    with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'w') as f:
        f.write(text)
        print("Removed Dialog block from DashboardScreen")
else:
    print("Could not find the block to remove")

