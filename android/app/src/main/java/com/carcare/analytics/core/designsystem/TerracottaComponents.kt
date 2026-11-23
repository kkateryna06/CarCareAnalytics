package com.carcare.analytics.core.designsystem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.carcare.analytics.R
import com.carcare.analytics.feature.logbook.EntryKind

enum class CareSymbol { CAR, CHART, JOURNAL, CALENDAR, BACK, PLUS }
@Composable fun CareIcon(symbol: CareSymbol,modifier: Modifier=Modifier) {
 val color=LocalContentColor.current
 Canvas(modifier.size(24.dp)) {
  val s=size.width/24f; val stroke=Stroke(1.7f*s)
  fun line(x:Float,y:Float,x2:Float,y2:Float) = drawLine(color,Offset(x*s,y*s),Offset(x2*s,y2*s),1.7f*s)
  fun rect(x:Float,y:Float,w:Float,h:Float)=drawRoundRect(color,Offset(x*s,y*s),Size(w*s,h*s),CornerRadius(2*s),style=stroke)
  when(symbol) {
   CareSymbol.CAR -> { rect(3f,10f,18f,8f);line(5f,10f,8f,5f);line(8f,5f,16f,5f);line(16f,5f,19f,10f);line(6f,13f,8f,13f);line(16f,13f,18f,13f);line(6f,18f,6f,21f);line(18f,18f,18f,21f) }
   CareSymbol.CHART -> { line(5f,20f,5f,12f);line(12f,20f,12f,4f);line(19f,20f,19f,8f) }
   CareSymbol.JOURNAL -> { rect(5f,4f,14f,17f);rect(9f,2f,6f,4f);line(8f,11f,16f,11f);line(8f,15f,16f,15f) }
   CareSymbol.CALENDAR -> { rect(3f,5f,18f,16f);line(3f,10f,21f,10f);line(8f,2f,8f,7f);line(16f,2f,16f,7f) }
   CareSymbol.BACK -> { line(19f,12f,5f,12f);line(5f,12f,11f,6f);line(5f,12f,11f,18f) }
   CareSymbol.PLUS -> { line(4f,12f,20f,12f);line(12f,4f,12f,20f) }
  }
 }
}
@Composable fun BackButton(onClick:()->Unit,enabled:Boolean=true) {
 val label=stringResource(R.string.back)
 IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.semantics { contentDescription=label }) { CareIcon(CareSymbol.BACK) }
}
@Composable fun VehicleBanner(title:String,detail:String) {
 OutlinedCard(Modifier.fillMaxWidth(),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
  Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
   Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer) { Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) { CareIcon(CareSymbol.CAR) } }
   Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium);Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
  }
 }
}
@Composable fun EmptyPanel(title:String,description:String="",action:(@Composable ()->Unit)?=null) {
 Column(Modifier.fillMaxWidth().padding(vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.secondaryContainer) { Box(Modifier.size(88.dp),contentAlignment=Alignment.Center) { CareIcon(CareSymbol.JOURNAL,Modifier.size(44.dp)) } }
  Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  if(description.isNotBlank()) Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  action?.invoke()
 }
}
@Composable fun AddOperationButton(onAdd:(EntryKind)->Unit,enabled:Boolean=true,largeLabel:Boolean=false) {
 var open by remember { mutableStateOf(false) }
 Button(onClick={open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) {
  CareIcon(CareSymbol.PLUS);Spacer(Modifier.width(10.dp));Text(stringResource(R.string.add_operation),style=if(largeLabel) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
 }
 if(open) AddOperationSheet(onDismiss={open=false},onAdd={open=false;onAdd(it)})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AddOperationSheet(onDismiss:()->Unit,onAdd:(EntryKind)->Unit) {
 ModalBottomSheet(onDismissRequest=onDismiss) {
  Column(Modifier.padding(horizontal=24.dp).navigationBarsPadding().padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Text(stringResource(R.string.add_operation),style=MaterialTheme.typography.headlineSmall)
   EntryKind.entries.filterNot{it==EntryKind.DOCUMENT}.forEach { kind ->
    OutlinedButton(onClick={onAdd(kind)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)) { Text(stringResource(kind.label)) }
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(value:String,onValueChange:(String)->Unit,label:String,enabled:Boolean=true,error:String?=null) {
 var open by remember { mutableStateOf(false) }
 val pickDate=stringResource(R.string.pick_date)
 OutlinedTextField(value=value,onValueChange=onValueChange,label={Text(label)},enabled=enabled,singleLine=true,
  shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),isError=error!=null,
  supportingText={if(error!=null) Text(error)},trailingIcon={
   IconButton(onClick={open=true},enabled=enabled,modifier=Modifier.semantics { contentDescription=pickDate }) { CareIcon(CareSymbol.CALENDAR) }
  })
 if(open) {
  val initial=remember(value) { runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
  val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
  DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={
   picker.selectedDateMillis?.let { onValueChange(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) };open=false
  },enabled=picker.selectedDateMillis!=null) { Text(stringResource(R.string.expense_date_confirm)) }},dismissButton={TextButton(onClick={open=false}) {Text(stringResource(R.string.cancel))}}) { DatePicker(picker) }
 }
}
@Composable fun LoadingCards() {
 val loading=stringResource(R.string.loading)
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription=loading })
  repeat(3) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Surface(Modifier.fillMaxWidth(0.7f).height(20.dp),shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {}
   Surface(Modifier.fillMaxWidth(0.45f).height(14.dp),shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {}
  } } }
 }
}
