package com.carcare.analytics.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carcare.analytics.R
import com.carcare.analytics.feature.logbook.*
import java.math.*

@Composable
fun decimalDisplay(value: String,scale: Int = 2): String = overviewNumber(value,scale)
fun reminderLabel(reminder: ReminderDto): Int = if(reminder.sourceKind=="DOCUMENT") DocumentType.entries.firstOrNull { it.name==reminder.type }?.label?:R.string.entry_document else ServiceType.entries.firstOrNull { it.name==reminder.type }?.label?:R.string.entry_service
private val ChartColors=listOf(Color(0xFFAF583D),Color(0xFFD68B6A),Color(0xFF80513F),Color(0xFFE8B899),Color(0xFFBA7958),Color(0xFF6B493A),Color(0xFFC7A38D))
internal fun expenseCategoryColor(category: ExpenseCategory): Color = ChartColors[category.ordinal]

@Composable
internal fun CategoryChart(categories: List<CategoryTotalDto>) {
    val entries = categories.filter { it.amountPln.toBigDecimal().signum() > 0 }
    Text(stringResource(R.string.category_chart), style = MaterialTheme.typography.titleLarge)
    if (entries.isEmpty()) {
        Text(stringResource(R.string.no_expenses))
        return
    }

    val total = entries.sumOf { it.amountPln.toBigDecimal() }
    val description = stringResource(R.string.chart_categories_description)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp).semantics { contentDescription = description }) {
            val stroke = 30.dp.toPx()
            val bounds = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            entries.forEach { entry ->
                val sweep = entry.amountPln.toBigDecimal().divide(total, 10, RoundingMode.HALF_UP).toFloat() * 360f
                drawArc(expenseCategoryColor(entry.category), start, sweep, false, Offset(stroke / 2, stroke / 2), bounds, style = Stroke(stroke))
                start += sweep
            }
        }
        Text(stringResource(R.string.money_pln, decimalDisplay(total.toPlainString())), style = MaterialTheme.typography.titleMedium)
    }
    entries.forEach { entry ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).background(expenseCategoryColor(entry.category)))
            Text(
                stringResource(R.string.category_share, stringResource(entry.category.label), decimalDisplay(entry.amountPln), entry.sharePercent ?: "0"),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MonthlyChart(months: List<MonthTotalDto>) {
    Text(stringResource(R.string.monthly_chart),style=MaterialTheme.typography.titleLarge)
    val maximum=months.maxOfOrNull { it.amountPln.toBigDecimal() }?.takeIf { it.signum()>0 }?:BigDecimal.ONE
    val color=MaterialTheme.colorScheme.primary; val background=MaterialTheme.colorScheme.surfaceVariant
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        items(months,key={ it.month }) { item ->
            val ratio=item.amountPln.toBigDecimal().divide(maximum,10,RoundingMode.HALF_UP).toFloat()
            val description=stringResource(R.string.chart_month_description,item.month,decimalDisplay(item.amountPln))
            Column(Modifier.width(80.dp).semantics(mergeDescendants=true) { contentDescription=description },horizontalAlignment=Alignment.CenterHorizontally) {
                Text(decimalDisplay(item.amountPln),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                Canvas(Modifier.height(140.dp).width(48.dp)) {
                    drawRoundRect(background,cornerRadius=CornerRadius(6.dp.toPx()))
                    val height=size.height*ratio
                    if(height>0) drawRoundRect(color,topLeft=Offset(0f,size.height-height),size=Size(size.width,height),cornerRadius=CornerRadius(6.dp.toPx()))
                }
                Text(item.month,style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}
