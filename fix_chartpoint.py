with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

# Add ChartPoint class in Entities and monthlyChartData in ViewModel

entities_update = """
data class ChartPoint(
    val day: Int,
    val income: Double,
    val expense: Double,
    val profit: Double
)
"""

with open('app/src/main/java/com/example/data/Entities.kt', 'a') as f:
    f.write(entities_update)

props_to_add = """
    val monthlyChartData = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ChartPoint>>(emptyList())
"""

index = vm_content.find('class HamrahanViewModelFactory')
if index != -1:
    index = vm_content.rfind('}', 0, index)
    vm_content = vm_content[:index] + props_to_add + vm_content[index:]

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)

# Update DashboardScreen to import ChartPoint
with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'r') as f:
    dash_content = f.read()

dash_content = dash_content.replace('import com.example.data.SystemAlert', 'import com.example.data.SystemAlert\nimport com.example.data.ChartPoint')

with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'w') as f:
    f.write(dash_content)

