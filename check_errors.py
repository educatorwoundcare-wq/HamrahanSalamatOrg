with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'minVersion' in line or 'latestVersion' in line:
        print(f"ViewModel {i+1}: {line.strip()}")
    if 'clearCurrentDeepLink' in line:
        print(f"ViewModel {i+1}: {line.strip()}")

with open('app/src/main/java/com/example/ui/PatientScreen.kt', 'r') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'insertReferral' in line:
        print(f"PatientScreen {i+1}: {line.strip()}")
    if 'clearCurrentDeepLink' in line:
        print(f"PatientScreen {i+1}: {line.strip()}")

with open('app/src/main/java/com/example/ui/RegistrationScreen.kt', 'r') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'id' in line or 'selectedServices' in line or 'reason' in line or 'comment' in line or 'registration =' in line:
        pass # Too many lines, we will just read RegistrationScreen instead.

with open('app/src/main/java/com/example/ui/ReportScreen.kt', 'r') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'exportDataToExcel' in line:
        print(f"ReportScreen {i+1}: {line.strip()}")
