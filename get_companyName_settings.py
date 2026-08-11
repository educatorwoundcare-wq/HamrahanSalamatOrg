with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'val companyName ' in line or 'val companyName:' in line or 'val companyName =' in line:
        print(f"Line {i+1}: {line.strip()}")
