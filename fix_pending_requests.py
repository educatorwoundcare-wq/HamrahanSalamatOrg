import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('val pendingRequests by viewModel.pendingDeviceRequests.collectAsState()', 'val pendingRequests by viewModel.livePendingDevices.collectAsState()')

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(text)
