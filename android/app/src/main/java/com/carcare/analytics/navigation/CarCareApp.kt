package com.carcare.analytics.navigation

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.carcare.analytics.core.designsystem.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.carcare.analytics.feature.auth.AuthScreen
import com.carcare.analytics.feature.vehicles.*
import com.carcare.analytics.feature.logbook.*
import com.carcare.analytics.feature.insights.DashboardScreen
import com.carcare.analytics.feature.reminders.RemindersScreen
import com.carcare.analytics.feature.forecast.ForecastScreen

@Composable
fun CarCareApp(
    notificationVehicleId: Long? = null,onNotificationHandled: ()->Unit = {},
    language: AppLanguage = AppLanguage.POLISH,onLanguageSelected: (AppLanguage) -> Unit = {},
    viewModel: SessionViewModel = hiltViewModel()
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    if (!ready) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val account=session
    key(account?.userId) {
        val nav = rememberNavController()
        if (account == null) {
            NavHost(nav, startDestination = "login") {
                composable("login") { AuthScreen(false,onSwitch={ nav.navigate("register") { launchSingleTop=true } },onLanguage={ languageOpen=true }) }
                composable("register") { AuthScreen(true,onSwitch={ nav.popBackStack() },onLanguage={ languageOpen=true }) }
            }
        } else {
            val context=LocalContext.current
            val preferences=remember(context) { context.getSharedPreferences("carcare_vehicle_selection",Context.MODE_PRIVATE) }
            val preferenceKey=account.userId.toString()
            var preferredVehicle by rememberSaveable {
                mutableStateOf(preferences.getLong(preferenceKey,0L).takeIf { it>0 })
            }
            val garageViewModel: VehicleListViewModel = hiltViewModel(key="garage:${account.userId}")
            val garage by garageViewModel.state.collectAsStateWithLifecycle()
            val selectVehicle: (Long) -> Unit = { id ->
                if(preferredVehicle!=id) {
                    preferredVehicle=id
                    preferences.edit().putLong(preferenceKey,id).apply()
                }
                garageViewModel.select(id)
            }
            val entry by nav.currentBackStackEntryAsState()
            val route=entry?.destination?.route.orEmpty()
            val documents=route.startsWith("logbook/") && entry?.arguments?.getString("tab")=="documents"
            val routeVehicle=entry?.arguments?.getString("vehicleId")?.toLongOrNull()?.takeIf { it>0 }
            LaunchedEffect(route,routeVehicle) {
                if(routeVehicle!=null && !route.startsWith("vehicle/")) selectVehicle(routeVehicle)
            }
            val showTabs=route=="vehicles" || route.startsWith("dashboard/") || route.startsWith("reminders/") || (route.startsWith("logbook/") && !route.contains("/entry/")) || route.startsWith("forecast/")
            Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
                if(showTabs) {
                    val active=when {
                        route=="vehicles" -> 0
                        documents -> 3
                        route.startsWith("reminders/") -> 3
                        route.startsWith("logbook/") -> 2
                        else -> 1
                    }
                    val vehicle=routeVehicle ?: garage.activeVehicle?.id
                    OverviewNavigation(active,vehicleSelected=vehicle!=null) { index ->
                        val destination=when(index) {
                            0 -> "vehicles"
                            1 -> vehicle?.let { "dashboard/$it" }
                            2 -> vehicle?.let { "logbook/$it" }
                            else -> vehicle?.let { "reminders/$it" }
                        }
                        if((index!=active || documents || route.startsWith("forecast/")) && destination!=null) {
                            nav.navigate(destination) { popUpTo("vehicles") { inclusive=false }; launchSingleTop=true }
                        }
                    }
                }
            }) { shellPadding ->
            NavHost(nav, startDestination = "vehicles",modifier=Modifier.padding(bottom=shellPadding.calculateBottomPadding())) {
                composable("vehicles") {
                    VehicleListScreen(
                        onAdd={ nav.navigate("vehicle/new") },onEdit={ nav.navigate("vehicle/$it") },
                        onOpen={ selectVehicle(it); nav.navigate("dashboard/$it") },onLogout=viewModel::logout,
                        preferredId=preferredVehicle,
                        onVehicleDeleted={
                            if(preferredVehicle==it) {
                                preferredVehicle=null
                                preferences.edit().remove(preferenceKey).apply()
                            }
                        },
                        onSettings={ languageOpen=true },
                        viewModel=garageViewModel
                    )
                }
                composable("vehicle/{vehicleId}", arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })) { VehicleFormScreen(onBack = { nav.popBackStack() }) }
                composable("dashboard/{vehicleId}",arguments=listOf(navArgument("vehicleId") { type=NavType.StringType })) { entry ->
                    val vehicle=checkNotNull(entry.arguments?.getString("vehicleId"))
                    DashboardScreen(onBack={ nav.navigate("vehicles") { popUpTo("vehicles") { inclusive=true }; launchSingleTop=true } },onJournal={ nav.navigate("logbook/$vehicle") },onReminders={ nav.navigate("reminders/$vehicle") },onForecast={ nav.navigate("forecast/$vehicle") },
                        onAdd={ nav.navigate("logbook/$vehicle/entry/${it.name}/new") })
                }
                composable("forecast/{vehicleId}",arguments=listOf(navArgument("vehicleId") { type=NavType.StringType })) { entry ->
                    val vehicle=checkNotNull(entry.arguments?.getString("vehicleId"))
                    ForecastScreen(onBack={ nav.popBackStack() },onAddExpense={ nav.navigate("logbook/$vehicle/entry/EXPENSE/new") })
                }
                composable("reminders/{vehicleId}",arguments=listOf(navArgument("vehicleId") { type=NavType.StringType })) { entry ->
                    val vehicle=checkNotNull(entry.arguments?.getString("vehicleId"))
                    RemindersScreen(onGarage={ nav.navigate("vehicles") { popUpTo("vehicles") { inclusive=false }; launchSingleTop=true } },onDocuments={ nav.navigate("logbook/$vehicle?tab=documents") },onEdit={ kind,id -> nav.navigate("logbook/$vehicle/entry/${kind.name}/$id") })
                }
                composable("logbook/{vehicleId}?tab={tab}",arguments=listOf(navArgument("vehicleId") { type=NavType.StringType },navArgument("tab") { type=NavType.StringType; defaultValue="history" })) { entry ->
                    val vehicle=checkNotNull(entry.arguments?.getString("vehicleId"))
                    LogbookScreen(
                        onGarage={ nav.navigate("vehicles") { popUpTo("vehicles") { inclusive=false }; launchSingleTop=true } },
                        onBack={ nav.popBackStack() },
                        onDocuments={ nav.navigate("logbook/$vehicle?tab=documents") },
                        onAdd={ nav.navigate("logbook/$vehicle/entry/${it.name}/new") },
                        onEdit={ kind,id -> nav.navigate("logbook/$vehicle/entry/${kind.name}/$id") }
                    )
                }
                composable("logbook/{vehicleId}/entry/{kind}/{recordId}",arguments=listOf(navArgument("vehicleId") { type=NavType.StringType },navArgument("kind") { type=NavType.StringType },navArgument("recordId") { type=NavType.StringType })) {
                    OperationFormScreen(onBack={ nav.popBackStack() })
                }
            }
            }
            LaunchedEffect(notificationVehicleId) { notificationVehicleId?.let { nav.navigate("reminders/$it") { launchSingleTop=true }; onNotificationHandled() } }
        }
    }
    if(languageOpen) LanguageSettingsDialog(language,onSelect={ languageOpen=false; onLanguageSelected(it) },onDismiss={ languageOpen=false })
    failure?.let { error -> AlertDialog(onDismissRequest = viewModel::dismissError, text = { Text(stringResource(error.fallback)) }, confirmButton = {
        TextButton(onClick = viewModel::dismissError) { Text(stringResource(com.carcare.analytics.R.string.cancel)) }
    }) }
}
