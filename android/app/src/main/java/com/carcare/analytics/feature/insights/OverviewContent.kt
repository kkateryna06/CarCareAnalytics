package com.carcare.analytics.feature.insights

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carcare.analytics.core.designsystem.*
import com.carcare.analytics.R
import com.carcare.analytics.feature.reminders.reminderDaysLabel
import com.carcare.analytics.core.network.VehicleDto
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun overviewNumber(value: String, decimals: Int = 2): String = value.toBigDecimalOrNull()?.let {
    NumberFormat.getNumberInstance(appLocale()).apply {
        minimumFractionDigits=decimals; maximumFractionDigits=decimals; roundingMode=RoundingMode.HALF_UP
    }.format(it)
} ?: value

@Composable
internal fun localizedMonth(value: String, pattern: String = "LLLL uuuu"): String {
    val locale=appLocale()
    return runCatching {
        YearMonth.parse(value).format(DateTimeFormatter.ofPattern(pattern,locale)).replaceFirstChar { it.titlecase(locale) }
    }.getOrDefault(value)
}

@Composable internal fun TerracottaVehicle(vehicle: VehicleDto, onSelect: (() -> Unit)? = null, compact: Boolean = false) {
    TerracottaVehicle(
        "${vehicle.make} ${vehicle.model}",
        "${vehicle.year} · ${stringResource(R.string.vehicle_odometer_value,overviewNumber(vehicle.currentOdometerKm.toString(),0))}",
        onSelect, vehicle, compact
    )
}

@Composable internal fun TerracottaVehicle(name: String, detail: String, onSelect: (() -> Unit)? = null, vehicle: VehicleDto? = null, compact: Boolean = false) {
    val modifier=Modifier.fillMaxWidth()
    val shape=RoundedCornerShape(18.dp)
    val border=BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))
    val colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=MaterialTheme.colorScheme.secondaryContainer, shape=RoundedCornerShape(12.dp)) {
                Box(Modifier.size(if (compact) 38.dp else 48.dp), contentAlignment=Alignment.Center) {
                    com.carcare.analytics.feature.vehicles.VehiclePhoto(vehicle?.id,vehicle?.photoVersion,Modifier.fillMaxSize()) {
                        OverviewIcon("car", Modifier.size(if (compact) 28.dp else 36.dp))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, fontSize=if (compact) 16.sp else 18.sp, fontWeight=FontWeight.Bold, maxLines=2, overflow=TextOverflow.Ellipsis)
                Text(detail, fontSize=14.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(onSelect!=null) OverviewIcon("down",Modifier.size(20.dp))
        }
    }
    if(onSelect==null) OutlinedCard(modifier,shape,colors,border=border,content=content)
    else OutlinedCard(onSelect,modifier,shape=shape,colors=colors,border=border,content=content)
}

@Composable internal fun OverviewHeading(period: AnalyticsPeriod?, onPeriod: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
        Text(stringResource(R.string.nav_overview),Modifier.weight(1f),fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
        FilledTonalButton(onClick=onPeriod,shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=10.dp)) {
            Text(period?.let { stringResource(R.string.forecast_months,it.monthCount) } ?: stringResource(R.string.period_short),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp)); OverviewIcon("down",Modifier.size(16.dp))
        }
    }
}

@Composable internal fun TerracottaTotal(label: String, summary: SummaryDto) {
    val locale = appLocale()
    val dates = remember(summary.period, locale) {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val from = LocalDate.parse(summary.period.dateFrom).format(formatter)
        val to = LocalDate.parse(summary.period.dateTo).format(formatter)
        if (from == to) from else "$from – $to"
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dates, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints {
            val formatted=overviewNumber(summary.totalExpensesPln)
            // Large amounts remain readable on narrow devices instead of clipping.
            val size=if(formatted.length>10 || maxWidth<330.dp) 32.sp else 40.sp
            Text(stringResource(R.string.money_pln, formatted),fontSize=size,lineHeight=size*1.15f,fontWeight=FontWeight.Bold,letterSpacing=(-1.5).sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(36.dp,3.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(2.dp)))
    }
}

@Composable internal fun OverviewMetrics(data: DashboardDto) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        OverviewMetric("fuel",data.fuel.averageLitersPer100Km?.let { overviewNumber(it) } ?: "—",stringResource(R.string.liters_per_100_unit),Modifier.weight(1f))
        OverviewMetric("road",data.summary.costPerKmPln?.let { overviewNumber(it) } ?: "—",stringResource(R.string.pln_per_km_unit),Modifier.weight(1f))
    }
}
@Composable private fun OverviewMetric(icon:String,value:String,unit:String,modifier:Modifier) {
    Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.7f)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OverviewIcon(icon,Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(value,fontSize=23.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)
                Text(unit,fontSize=12.sp,lineHeight=16.sp)
            }
        }
    }
}

@Composable
internal fun OverviewChart(months: List<MonthTotalDto>) {
    val highest = remember(months) {
        months.maxOfOrNull { it.amountPln.toBigDecimalOrNull() ?: BigDecimal.ZERO } ?: BigDecimal.ZERO
    }
    if (highest.signum() <= 0) {
        EmptyOverviewChart()
        return
    }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = if (months.size <= 6) maxWidth / months.size.coerceAtLeast(3) else 68.dp
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.Bottom) {
            months.forEachIndexed { index, month ->
                OverviewChartBar(month, highest.max(BigDecimal.ONE), cell, highlightBar = index == months.lastIndex)
            }
        }
    }
}

@Composable
private fun EmptyOverviewChart() {
    Box(Modifier.fillMaxWidth().height(156.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.chart_empty), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, lineHeight = 24.sp
        )
    }
}

@Composable
internal fun OverviewMonthChart(months: List<MonthTotalDto>, selectedMonth: YearMonth?, compact: Boolean, onSelect: (String) -> Unit) {
    if (months.isEmpty()) {
        EmptyOverviewChart()
        return
    }
    val columnsOnScreen = 3
    // One leading slot lets the oldest month reach the center. The last month still ends at the right edge.
    val lastPage = (months.size + 1 - columnsOnScreen).coerceAtLeast(0)
    val maximum = remember(months) {
        months.maxOf { it.amountPln.toBigDecimalOrNull() ?: BigDecimal.ZERO }.max(BigDecimal.ONE)
    }
    key(months.first().month, months.last().month) {
        val selectedIndex = months.indexOfFirst { it.month == selectedMonth?.toString() }
        val chartScroll = rememberLazyListState(initialFirstVisibleItemIndex = if (selectedIndex < 0) lastPage else selectedIndex.coerceAtMost(lastPage))
        val scope = rememberCoroutineScope()
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.monthly_chart), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                ChartArrow("left", R.string.chart_previous_months, chartScroll.canScrollBackward) {
                    scope.launch {
                        chartScroll.animateScrollToItem((chartScroll.firstVisibleItemIndex - columnsOnScreen).coerceAtLeast(0))
                    }
                }
                ChartArrow("right", R.string.chart_next_months, chartScroll.canScrollForward) {
                    scope.launch {
                        chartScroll.animateScrollToItem((chartScroll.firstVisibleItemIndex + columnsOnScreen).coerceAtMost(lastPage))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = with(LocalDensity.current) { (maxWidth.roundToPx() / columnsOnScreen).toDp() }
                LazyRow(Modifier.fillMaxWidth().selectableGroup(), state = chartScroll) {
                    item(key = "leading-slot") { Spacer(Modifier.width(cell)) }
                    itemsIndexed(months, key = { _, month -> month.month }) { index, month ->
                        OverviewChartBar(
                            month, maximum, cell,
                            selected = month.month == selectedMonth?.toString(),
                            plotHeight = if (compact) 88.dp else 116.dp,
                            onSelect = {
                                onSelect(month.month)
                                scope.launch { chartScroll.animateScrollToItem(index.coerceAtMost(lastPage)) }
                            }
                        )
                    }
                }
            }
            if (!compact && selectedMonth == null) {
                Text(
                    stringResource(R.string.chart_select_month),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartArrow(icon: String, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
    IconButton(onClick, Modifier.semantics { contentDescription = description }, enabled = enabled) {
        OverviewIcon(icon, Modifier.size(18.dp), color)
    }
}

@Composable
private fun OverviewChartBar(
    month: MonthTotalDto, maximum: BigDecimal, width: Dp,
    selected: Boolean = false, highlightBar: Boolean = selected, plotHeight: Dp = 130.dp, onSelect: (() -> Unit)? = null
) {
    val amount = month.amountPln.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val ratio = amount.divide(maximum, 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    val interactive = onSelect != null
    val caption = localizedMonth(month.month, if (interactive) "LLL yy" else "LLL").trimEnd('.')
    val description = stringResource(R.string.chart_month_description, localizedMonth(month.month), overviewNumber(month.amountPln))
    val primary = MaterialTheme.colorScheme.primary
    val target = if (onSelect != null) {
        Modifier.clip(RoundedCornerShape(9.dp)).selectable(selected = selected, role = Role.Tab, onClick = onSelect)
    } else {
        Modifier
    }
    Column(
        Modifier.width(width).semantics(mergeDescendants = true) { contentDescription = description }.then(target),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.height(plotHeight).fillMaxWidth().padding(horizontal = 7.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                overviewNumber(month.amountPln, if (amount.stripTrailingZeros().scale() > 0) 2 else 0),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(((plotHeight - 34.dp) * ratio).coerceAtLeast(if (interactive) 2.dp else 1.dp)).background(
                    if (highlightBar) primary else Color(0xFFDCA68D), RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
                )
            )
        }
        HorizontalDivider(color = if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f))
        Text(
            caption, Modifier.padding(top = 6.dp, bottom = if (interactive) 6.dp else 0.dp),
            fontSize = if (interactive) 13.sp else 14.sp,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
internal fun OverviewMonthReport(selection: DashboardMonthState, onRetry: () -> Unit, onClose: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(localizedMonth(selection.month.toString()), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                val close = stringResource(R.string.chart_close_month)
                IconButton(onClose, Modifier.semantics { contentDescription = close }) {
                    OverviewIcon("close", Modifier.size(18.dp))
                }
            }
            selection.data?.let { data ->
                Text(
                    stringResource(R.string.money_pln, overviewNumber(data.summary.totalExpensesPln)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val categories = data.categories.content.filter { (it.amountPln.toBigDecimalOrNull()?.signum() ?: 0) > 0 }
                if (categories.isEmpty()) {
                    Text(stringResource(R.string.chart_month_no_expenses), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            MonthReportLine(
                                stringResource(category.category.label),
                                stringResource(R.string.money_pln, overviewNumber(category.amountPln)),
                                expenseCategoryColor(category.category)
                            )
                        }
                    }
                }
                val distance = data.summary.distanceKm
                val cost = data.summary.costPerKmPln
                val consumption = data.fuel.averageLitersPer100Km
                if (distance != null || cost != null || consumption != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        distance?.let { MonthReportLine(stringResource(R.string.distance), stringResource(R.string.vehicle_odometer_value, overviewNumber(it.toString(), 0))) }
                        cost?.let { MonthReportLine(stringResource(R.string.cost_per_km), stringResource(R.string.pln_per_km, overviewNumber(it))) }
                        consumption?.let { MonthReportLine(stringResource(R.string.fuel_consumption), stringResource(R.string.liters_per_100, overviewNumber(it))) }
                    }
                }
            }
            if (selection.loading && selection.data == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            selection.failure?.let { FailureCard(it, onRetry, showingCachedData = selection.data != null) }
        }
    }
}

@Composable
private fun MonthReportLine(label: String, value: String, dot: Color? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape)) }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable internal fun OverviewDeadline(data:DashboardDto,onClick:()->Unit) {
    val reminder=data.reminders.content.filter { it.status!=ReminderStatus.DONE }.map { r ->
        val evaluated=com.carcare.analytics.feature.reminders.evaluateReminder(r,java.time.LocalDate.now(),data.vehicle.currentOdometerKm)
        r.copy(status=evaluated.status,remainingDays=evaluated.remainingDays,remainingKm=evaluated.remainingKm)
    }.sortedWith(compareBy<ReminderDto> { it.status.ordinal }.thenBy { it.remainingDays ?: Long.MAX_VALUE }.thenBy { it.remainingKm ?: Long.MAX_VALUE }).firstOrNull()
    Surface(onClick=onClick,shape=RoundedCornerShape(14.dp),color=Color(0xFFFFEFE3),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OverviewIcon("shield",Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(reminder?.let(::reminderLabel) ?: R.string.next_deadlines),fontSize=15.sp,fontWeight=FontWeight.Medium)
                val detail=reminder?.let { r ->
                    val days=r.remainingDays
                    when {
                        r.status==ReminderStatus.OVERDUE -> stringResource(R.string.deadline_missed)
                        days!=null -> reminderDaysLabel(days)
                        r.remainingKm!=null -> stringResource(R.string.distance_left,overviewNumber(r.remainingKm.toString(),0))
                        else -> r.dueDate ?: stringResource(R.string.view_deadline)
                    }
                } ?: stringResource(R.string.reminders_empty_title)
                Text(detail,fontSize=13.sp,color=if(reminder?.status==ReminderStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            OverviewIcon("right",Modifier.size(18.dp))
        }
    }
}

/** One consistent set of thin outline symbols for the overview. */
@Composable internal fun OverviewIcon(kind:String,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val s=size.width/24f
        val outline=Stroke(1.6f*s,cap=StrokeCap.Round)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x*s,y*s),Offset(x2*s,y2*s),1.6f*s,StrokeCap.Round)
        fun path(vararg p:Float) { val path=Path();path.moveTo(p[0]*s,p[1]*s); for(i in 2 until p.size step 2) path.lineTo(p[i]*s,p[i+1]*s);drawPath(path,color,style=Stroke(1.6f*s,cap=StrokeCap.Round)) }
        when(kind) {
            "fuel" -> { path(3f,21f,3f,3f,13f,3f,13f,21f);line(2f,21f,15f,21f);path(3f,10f,13f,10f);path(13f,12f,16f,12f,16f,18f,20f,18f,20f,8f,17f,5f);line(18f,6f,18f,10f) }
            "road" -> { line(7f,3f,2f,21f);line(17f,3f,22f,21f);line(12f,3f,12f,7f);line(12f,11f,12f,15f);line(12f,19f,12f,21f) }
            "shield" -> path(12f,2f,21f,6f,20f,15f,17f,19f,12f,22f,7f,19f,4f,15f,3f,6f,12f,2f)
            "down" -> path(6f,9f,12f,15f,18f,9f)
            "up" -> path(6f,15f,12f,9f,18f,15f)
            "close" -> { line(6f,6f,18f,18f);line(18f,6f,6f,18f) }
            "check" -> path(4f,12f,9f,17f,20f,6f)
            "language" -> {
                drawCircle(color,9.5f*s,style=outline)
                drawOval(color,Offset(7.3f*s,2.5f*s),androidx.compose.ui.geometry.Size(9.4f*s,19f*s),style=outline)
                line(3f,12f,21f,12f)
            }
            "warning" -> {
                path(12f,3f,22f,20f,2f,20f,12f,3f)
                line(12f,9f,12f,14f)
                drawCircle(color,.85f*s,Offset(12f*s,17f*s))
            }
            "mail" -> {
                path(3f,5f,21f,5f,21f,19f,3f,19f,3f,5f)
                path(3f,6f,12f,13f,21f,6f)
            }
            "lock" -> {
                drawRoundRect(color,Offset(5f*s,10f*s),androidx.compose.ui.geometry.Size(14f*s,11f*s),androidx.compose.ui.geometry.CornerRadius(2f*s),style=outline)
                drawArc(color,180f,180f,false,Offset(8f*s,3f*s),androidx.compose.ui.geometry.Size(8f*s,8f*s),style=outline)
                line(8f,7f,8f,10f)
                line(16f,7f,16f,10f)
            }
            "eye", "eyeOff" -> {
                val eye=Path().apply {
                    moveTo(2f*s,12f*s)
                    cubicTo(7f*s,4f*s,17f*s,4f*s,22f*s,12f*s)
                    cubicTo(17f*s,20f*s,7f*s,20f*s,2f*s,12f*s)
                }
                drawPath(eye,color,style=outline)
                drawCircle(color,3f*s,Offset(12f*s,12f*s),style=outline)
                if(kind=="eyeOff") line(4f,3f,20f,21f)
            }
            "logout" -> { path(11f,3f,4f,3f,4f,21f,11f,21f);line(9f,12f,21f,12f);path(16f,7f,21f,12f,16f,17f) }
            "more" -> { for(y in listOf(5f,12f,19f)) drawCircle(color,1.55f*s,Offset(12f*s,y*s)) }
            "trash" -> {
                path(3f,6f,21f,6f)
                path(9f,6f,9f,3f,15f,3f,15f,6f)
                path(5.5f,6f,7f,21f,17f,21f,18.5f,6f)
                path(10f,10f,10f,17f)
                path(14f,10f,14f,17f)
            }
            "right" -> path(9f,5f,16f,12f,9f,19f)
            "left" -> path(15f,5f,8f,12f,15f,19f)
            "service" -> path(20f,3f,16f,7f,17f,10f,20f,11f,23f,7f,22f,12f,18f,15f,13f,14f,6f,22f,3f,22f,2f,19f,10f,11f,9f,7f,12f,3f,16f,2f,20f,3f)
            "expense" -> { path(21f,8f,21f,5f,4f,5f,3f,7f,3f,19f,21f,19f,21f,15f);path(21f,8f,16f,8f,14f,10f,14f,13f,16f,15f,22f,15f,22f,8f,21f,8f);line(17f,11.5f,18f,11.5f) }
            "filter" -> { line(3f,6f,21f,6f);line(6f,12f,18f,12f);line(9f,18f,15f,18f) }
            "drop" -> {
                val drop=Path().apply {
                    moveTo(12f*s,2f*s)
                    cubicTo(9f*s,6f*s,5f*s,10f*s,5f*s,15f*s)
                    cubicTo(5f*s,24f*s,19f*s,24f*s,19f*s,15f*s)
                    cubicTo(19f*s,10f*s,15f*s,6f*s,12f*s,2f*s)
                    close()
                }
                drawPath(drop,color,style=outline)
            }
            "info" -> { drawCircle(color,9.5f*s,style=outline);line(12f,11f,12f,17f);drawCircle(color,.8f*s,Offset(12f*s,7.5f*s)) }
            "bell" -> {
                val bell=Path().apply {
                    moveTo(3f*s,17f*s);lineTo(5f*s,14f*s);lineTo(5f*s,9f*s)
                    cubicTo(5f*s,2f*s,19f*s,2f*s,19f*s,9f*s)
                    lineTo(19f*s,14f*s);lineTo(21f*s,17f*s);close()
                    moveTo(9f*s,20f*s);quadraticTo(12f*s,24f*s,15f*s,20f*s)
                }
                drawPath(bell,color,style=outline);line(12f,2f,12f,4f)
            }
            "refresh" -> {
                drawArc(color,40f,285f,false,Offset(4f*s,4f*s),androidx.compose.ui.geometry.Size(16f*s,16f*s),style=outline)
                path(19f,3f,19f,9f,13f,9f)
            }
            "download" -> { line(12f,3f,12f,15f);path(7f,11f,12f,16f,17f,11f);path(4f,16f,4f,21f,20f,21f,20f,16f) }
            "bars" -> { line(5f,20f,5f,14f);line(12f,20f,12f,9f);line(19f,20f,19f,3f) }
            "scale" -> {
                line(12f,3f,12f,21f);line(8f,21f,16f,21f);line(4f,6f,20f,6f)
                path(5f,6f,1.5f,15f,8.5f,15f,5f,6f)
                path(19f,6f,15.5f,15f,22.5f,15f,19f,6f)
            }
            "title" -> { path(6f,3f,18f,3f,20f,5f,20f,19f,18f,21f,6f,21f,4f,19f,4f,5f,6f,3f);line(8f,9f,16f,9f);line(8f,14f,14f,14f) }
            "note" -> { path(3f,21f,4f,15f,16f,3f,21f,8f,9f,20f,3f,21f);line(13f,6f,18f,11f) }
            "money" -> {
                val stroke=Stroke(1.55f*s)
                drawOval(color,Offset(3f*s,3f*s),androidx.compose.ui.geometry.Size(18f*s,7f*s),style=stroke)
                for(y in listOf(6.5f,12f,17f)) {
                    drawArc(color,0f,180f,false,Offset(3f*s,(y-3.5f)*s),androidx.compose.ui.geometry.Size(18f*s,7f*s),style=stroke)
                }
                line(3f,6.5f,3f,17f);line(21f,6.5f,21f,17f)
            }
            "car" -> { path(2f,16f,2f,11f,5f,10f,8f,5f,16f,5f,20f,10f,22f,12f,22f,16f,20f,16f);line(7f,16f,17f,16f);line(6f,10f,19f,10f);line(12f,5f,12f,10f);drawCircle(color,2.2f*s,Offset(5f*s,16f*s),style=Stroke(1.5f*s));drawCircle(color,2.2f*s,Offset(18f*s,16f*s),style=Stroke(1.5f*s)) }
        }
    }
}
