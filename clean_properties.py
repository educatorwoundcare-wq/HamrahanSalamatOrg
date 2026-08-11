with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip().startswith('val companyName =') \
        or line.strip().startswith('val taxPercentage =') \
        or line.strip().startswith('val defaultCurrency =') \
        or line.strip().startswith('val isDarkMode =') \
        or line.strip().startswith('val autoGenerateFixedExpenses =') \
        or line.strip().startswith('val fieldActiveSubmitter =') \
        or line.strip().startswith('val fieldActiveReceipt =') \
        or line.strip().startswith('val fieldActiveDescription =') \
        or line.strip().startswith('val fieldActivePaymentMethod =') \
        or line.strip().startswith('val moduleDonutChart =') \
        or line.strip().startswith('val moduleBarChart =') \
        or line.strip().startswith('val moduleHeatMap =') \
        or line.strip().startswith('val moduleStatCards =') \
        or line.strip().startswith('val userRole =') \
        or line.strip().startswith('val companyAddress =') \
        or line.strip().startswith('val companyPostalCode ='):
        # Ignore these since they are ambiguous with state flows? Wait, let's see why there is an overload resolution ambiguity.
        continue
    new_lines.append(line)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.writelines(new_lines)
