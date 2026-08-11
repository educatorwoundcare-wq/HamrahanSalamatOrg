import re
with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

# Fix saveStaffProfile
vm_content = re.sub(
    r'fun saveStaffProfile\(profile: com\.example\.data\.StaffProfile\) \{\}',
    r'''fun saveStaffProfile(profile: com.example.data.StaffProfile) {
        viewModelScope.launch {
            if (profile.id == 0) {
                repository.insertStaffProfile(profile)
            } else {
                repository.updateStaffProfile(profile)
            }
        }
    }''',
    vm_content
)

# Fix saveContract
vm_content = re.sub(
    r'fun saveContract\(contract: com\.example\.data\.Contract\) \{\}',
    r'''fun saveContract(contract: com.example.data.Contract) {
        viewModelScope.launch {
            if (contract.id == 0) {
                repository.insertContract(contract)
            } else {
                repository.updateContract(contract)
            }
        }
    }''',
    vm_content
)

# Fix insertReferral
vm_content = re.sub(
    r'fun insertReferral\(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: \(Long\) -> Unit\) \{\}',
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
    vm_content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
