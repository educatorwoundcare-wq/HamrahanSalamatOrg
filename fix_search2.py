with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

vm_content = vm_content.replace('val globalSearchResults = kotlinx.coroutines.flow.MutableStateFlow<List<Any>>(emptyList())', 'val globalSearchResults = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.SearchResults>(com.example.data.SearchResults(emptyList(), emptyList(), emptyList(), emptyList()))')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
