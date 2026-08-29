import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('val moduleDonutChartStr by viewModel.getSystemSetting("module_donut_chart").collectAsState(initial = "true")', '')
content = content.replace('val moduleLineChartStr by viewModel.getSystemSetting("module_line_chart").collectAsState(initial = "true")', '')
content = content.replace('val moduleDonutChart = moduleDonutChartStr == "true"', 'val moduleDonutChart by viewModel.moduleDonutChart.collectAsState()')
content = content.replace('val moduleLineChart = moduleLineChartStr == "true"', '')

content = content.replace('val integrityReport by viewModel.financialIntegrityReport.collectAsState()', 'val integrityReport by viewModel.integrityReport.collectAsState()')
content = content.replace('val auditLogs by viewModel.auditLogs.collectAsState()', '')
content = content.replace('val editHistories by viewModel.editHistories.collectAsState()', '')

# Replace other missing modules
missing_vars = [
    'moduleDailyAverage',
    'moduleFixedExpensesGenerator',
    'autoGenerateFixedExpenses',
    'fieldActiveSubmitter',
    'fieldActiveReceipt',
    'fieldActiveDescription',
    'fieldActivePaymentMethod',
    'requireManagerApprovalLarge'
]

insert_str = ""
for var in missing_vars:
    insert_str += f"    val {var} by viewModel.{var}.collectAsState()\n"

content = content.replace('val defaultCurrency by viewModel.defaultCurrency.collectAsState()', 'val defaultCurrency by viewModel.defaultCurrency.collectAsState()\n' + insert_str)

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)
