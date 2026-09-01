import re
with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'r') as f:
    text = f.read()

pattern = r'\s*// --- Primary Rendering of Pending Requests ---.*?if \(isMasterDevice\) \{[^{]*item \{[^}]*com\.example\.ui\.components\.PairingRequestsSection.*?\n\s*\}\s*\}\s*\}\n'

# Just slice it out manually by finding indices
start = text.find('// --- Primary Rendering of Pending Requests ---')
if start != -1:
    end = text.find('// --- Header Banner ---')
    if end != -1:
        text = text[:start] + text[end:]

with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'w') as f:
    f.write(text)
