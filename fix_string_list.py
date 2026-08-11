with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re
methods_to_fix = [
    'recalculateDashboardTotals',
    'recalculateCashboxBalances',
    'removeOrphanLedgerEntries',
    'repairBrokenReferences',
    'validateAndRepairFinancialIntegrity',
    'scanFinancialIntegrityIssues',
    'refreshFinancialIndexes',
    'clearLedger',
    'rebuildFinancialLedger'
]

for method in methods_to_fix:
    content = content.replace(
        f'fun {method}(onComplete: (String) -> Unit = {{}}) {{}}',
        f'fun {method}(onComplete: (List<String>) -> Unit = {{}}) {{}}'
    )

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
