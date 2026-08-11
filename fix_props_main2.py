with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("val connectedDevices = kotlinx.coroutines.flow.MutableStateFlow<List<Any>>(emptyList())", "val connectedDevices = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ConnectedDevice>>(emptyList())")

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
