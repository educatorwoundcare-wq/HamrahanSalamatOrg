import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val dashboardMetrics: StateFlow<DashboardMetrics> = kotlinx.coroutines.flow.MutableStateFlow(DashboardMetrics())',
    '''val dashboardMetrics: StateFlow<DashboardMetrics> = kotlinx.coroutines.flow.combine(
        repository.allServiceRegistrations,
        repository.allExpenses,
        repository.allFinancialTransactions
    ) { registrations, expenses, transactions ->
        val activeRegs = registrations.filter { !it.isDeleted }
        val activeExpenses = expenses.filter { !it.isDeleted }
        
        val todayIncome = activeRegs.sumOf { it.finalPrice } + transactions.filter { it.type == "درآمد" && (it.referenceId == null || it.referenceId == 0) }.sumOf { it.amount }
        val todayExpense = activeExpenses.sumOf { it.amount } + activeRegs.sumOf { it.employeeCommission } + transactions.filter { it.type == "هزینه" && (it.referenceId == null || it.referenceId == 0) }.sumOf { it.amount }
        
        val serviceTotal = activeRegs.sumOf { it.sellingPrice }
        val consumablesTotal = activeRegs.sumOf { it.otherCosts }
        val companyConsumables = activeRegs.filter { it.consumablesOwner == "Company" }.sumOf { it.otherCosts }
        val nurseConsumables = activeRegs.filter { it.consumablesOwner == "Nurse" }.sumOf { it.otherCosts }
        val companyRevenue = activeRegs.sumOf { it.companyProfit }
        val nurseCommission = activeRegs.sumOf { it.employeeCommission }
        
        DashboardMetrics(
            todayIncome = todayIncome,
            todayExpense = todayExpense,
            serviceTotal = serviceTotal,
            consumablesTotal = consumablesTotal,
            companyConsumables = companyConsumables,
            nurseConsumables = nurseConsumables,
            companyRevenue = companyRevenue,
            nurseCommission = nurseCommission,
            monthlyIncome = todayIncome,
            monthlyExpense = todayExpense,
            netProfit = todayIncome - todayExpense,
            projectedNetProfit = todayIncome - todayExpense
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())'''
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
