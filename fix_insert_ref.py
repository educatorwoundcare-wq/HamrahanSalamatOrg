with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'fun insertReferral\(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: \(Boolean\) -> Unit\) \{\}',
    r'''fun insertReferral(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            val ref = com.example.data.Referral(
                name = name,
                type = type,
                phone = phone,
                address = address,
                commissionPercentage = commissionPercentage,
                commissionFixedAmount = commissionFixedAmount,
                notes = notes
            )
            val id = repository.insertReferral(ref)
            onComplete(id)
        }
    }''',
    content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
