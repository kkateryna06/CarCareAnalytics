package com.carcare.analytics.feature.logbook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalChoice(label: Int,value: String,options: List<Pair<String,Int>>,enabled: Boolean = true,onChange: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected=options.firstOrNull { it.first==value }?.second
    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={ if(enabled) expanded=it }) {
        OutlinedTextField(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp), value=selected?.let { stringResource(it) }.orEmpty(),onValueChange={},readOnly=true,label={ Text(stringResource(label)) },
            enabled=enabled,trailingIcon={ ExposedDropdownMenuDefaults.TrailingIcon(expanded) },modifier=Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded=expanded,onDismissRequest={ expanded=false }) {
            options.forEach { (key,labelRes)-> DropdownMenuItem(text={ Text(stringResource(labelRes)) },onClick={ expanded=false; onChange(key) }) }
        }
    }
}
