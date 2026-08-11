import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

def replacer(entity_name):
    global vm_content
    save_regex = r'fun save' + entity_name + r'\(([^:]+): com\.example\.data\.' + entity_name + r'\) \{\}'
    save_repl = r'''fun save''' + entity_name + r'''(\1: com.example.data.''' + entity_name + r''') {
        viewModelScope.launch {
            if (\1.id == 0) repository.insert''' + entity_name + r'''(\1)
            else repository.update''' + entity_name + r'''(\1)
        }
    }'''
    vm_content = re.sub(save_regex, save_repl, vm_content)
    
    del_regex = r'fun delete' + entity_name + r'\(([^:]+): com\.example\.data\.' + entity_name + r'\) \{\}'
    del_repl = r'''fun delete''' + entity_name + r'''(\1: com.example.data.''' + entity_name + r''') {
        viewModelScope.launch {
            repository.delete''' + entity_name + r'''(\1)
        }
    }'''
    vm_content = re.sub(del_regex, del_repl, vm_content)

entities = [
    "Patient", "Employee", "Service", "ServiceSchedule", 
    "NursingReport", "VitalSigns", "WoundRecord", "ConsentForm", 
    "Prescription"
]

for e in entities:
    replacer(e)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
