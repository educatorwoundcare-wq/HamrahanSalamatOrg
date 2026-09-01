import re
with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'r') as f:
    text = f.read()

# Remove PAIRING logs
text = re.sub(r'\s*android\.util\.Log\.d\("PAIRING_[A-Z_]+", "[^"]+"\)\s*', '\n', text)
text = re.sub(r'android\.util\.Log\.d\("PAIRING_DIAG", "[^"]+"\)\s*', '', text)

# Remove the Forensic and Primary sections
pattern_forensic = r'\s*// --- Forensic Temporary Marker ---.*?item \{\s*if \(androidx\.compose\.ui\.platform\.LocalInspectionMode\.current \|\| true\) \{\s*Card.*?PAIRING DEBUG.*?\}\s*\}\s*\}'
text = re.sub(pattern_forensic, '', text, flags=re.DOTALL)

pattern_primary = r'\s*// --- Primary Rendering of Pending Requests ---.*?if \(isMasterDevice\) \{\s*item \{\s*com\.example\.ui\.components\.PairingRequestsSection\([^)]+\)\s*\}\s*\}'
text = re.sub(pattern_primary, '', text, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'w') as f:
    f.write(text)
