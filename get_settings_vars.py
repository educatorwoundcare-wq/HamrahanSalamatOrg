with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'companyName' in line or 'taxPercentage' in line or 'defaultCurrency' in line or 'moduleDonutChart' in line:
        print(f"Line {i+1}: {line.strip()}")
