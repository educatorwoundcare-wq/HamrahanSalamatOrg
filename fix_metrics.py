with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

dashboard_metrics_code = """
data class DashboardMetrics(
    val todayIncome: Double = 0.0,
    val todayExpense: Double = 0.0,
    val todayScheduledExpense: Double = 0.0,
    val todayPaidExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val monthlyScheduledExpense: Double = 0.0,
    val monthlyPaidExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val projectedNetProfit: Double = 0.0,
    val outstandingReceivables: Double = 0.0,
    val outstandingPayables: Double = 0.0,
    val activePatients: Int = 0,
    val employeeCount: Int = 0,
    val completedVisits: Int = 0,
    val serviceTotal: Double = 0.0,
    val consumablesTotal: Double = 0.0,
    val companyConsumables: Double = 0.0,
    val nurseConsumables: Double = 0.0,
    val companyRevenue: Double = 0.0,
    val nurseCommission: Double = 0.0,
    val topEmployee: String = "-",
    val mostRequestedService: String = "-"
)

"""
if 'data class DashboardMetrics' not in content:
    # insert it before the viewmodel class
    content = content.replace('class HamrahanViewModel', dashboard_metrics_code + '\nclass HamrahanViewModel')
    
    # insert the stateflow inside the viewmodel
    stateflow_code = """
    val dashboardMetrics: StateFlow<DashboardMetrics> = kotlinx.coroutines.flow.MutableStateFlow(DashboardMetrics())
    val activeAlerts: StateFlow<List<com.example.data.Alert>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    """
    content = content.replace('// --- Core Data Flows ---', stateflow_code + '\n    // --- Core Data Flows ---')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
