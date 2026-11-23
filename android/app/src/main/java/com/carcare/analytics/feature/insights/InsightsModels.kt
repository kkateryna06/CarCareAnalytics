package com.carcare.analytics.feature.insights

import com.carcare.analytics.core.network.VehicleDto
import com.carcare.analytics.feature.logbook.*

data class AnalyticsPeriod(val dateFrom: String,val dateTo: String,val monthCount: Int)
data class DistanceWindow(val firstDate: String,val lastDate: String,val firstOdometerKm: Int,val lastOdometerKm: Int)
data class SummaryDto(val period: AnalyticsPeriod,val totalExpensesPln: String,val monthAveragePln: String,val distanceKm: Long?,val costPerKmPln: String?,val recordCount: Long,val distanceWindow: DistanceWindow?,val warnings: List<String>)
data class CategoryTotalDto(val category: ExpenseCategory,val amountPln: String,val sharePercent: String?)
data class CategoriesDto(val period: AnalyticsPeriod,val content: List<CategoryTotalDto>)
data class MonthTotalDto(val month: String,val amountPln: String)
data class MonthlyDto(val period: AnalyticsPeriod,val content: List<MonthTotalDto>)
data class FuelDto(val period: AnalyticsPeriod,val totalLiters: String,val totalCostPln: String,val averagePricePerLiterPln: String?,val averageLitersPer100Km: String?,val measuredDistanceKm: Long,val measuredLiters: String,val cycleCount: Int,val refuelingCount: Int,val warnings: List<String>)
enum class ReminderStatus { OVERDUE, DUE, UPCOMING, SCHEDULED, DONE }
data class ReminderDto(val key: String,val sourceKind: String,val sourceId: Long,val type: String,val title: String,val dueDate: String?,val dueOdometerKm: Int?,val remainingDays: Long?,val remainingKm: Long?,val status: ReminderStatus,val notificationStage: String?,val notificationKey: String?)
data class RemindersDto(val asOfDate: String,val timeZone: String,val currentOdometerKm: Int,val content: List<ReminderDto>)
data class DashboardDto(val vehicle: VehicleDto,val summary: SummaryDto,val categories: CategoriesDto,val monthly: MonthlyDto,val fuel: FuelDto,val lifetimeExpensesPln: String,val currentMonthExpensesPln: String,val reminders: RemindersDto,val recentOperations: List<TimelineDto>)
data class CachedVehicleReminders(val vehicleId: Long,val vehicleLabel: String,val reminders: RemindersDto,val syncedAt: String)
data class ReminderCache(val userId: Long,val vehicles: List<CachedVehicleReminders>)
