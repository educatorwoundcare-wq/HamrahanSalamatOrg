with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re

# Fix insertReferral
content = re.sub(
    r'fun insertReferral\(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: \(Long\) -> Unit\) \{',
    r'fun insertReferral(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: (Long) -> Unit = {}) {',
    content
)

# Fix deleteReferral
content = re.sub(
    r'fun deleteReferral\(referral: com\.example\.data\.Referral\) \{',
    r'fun deleteReferral(referral: com.example.data.Referral, onComplete: (String) -> Unit = {}) {',
    content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
