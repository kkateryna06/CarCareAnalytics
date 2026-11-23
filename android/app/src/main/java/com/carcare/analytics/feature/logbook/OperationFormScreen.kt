package com.carcare.analytics.feature.logbook

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.DeleteConfirmationDialog

@Composable
fun OperationFormScreen(onBack: () -> Unit, viewModel: OperationFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    val dismissDelete = { deleteOpen = false }
    val confirmDelete = { deleteOpen = false; viewModel.delete() }

    LaunchedEffect(state.done) { if (state.done) onBack() }
    TerracottaOperationForm(viewModel,state,onBack,onDelete={ deleteOpen=true })
    if (deleteOpen) {
        val description = when(viewModel.kind) {
            EntryKind.DOCUMENT -> R.string.document_delete_description
            EntryKind.ODOMETER -> R.string.odometer_delete_description
            EntryKind.EXPENSE -> R.string.delete_expense_description
            else -> R.string.delete_operation_description
        }
        DeleteConfirmationDialog(
            title=stringResource(if(viewModel.kind==EntryKind.DOCUMENT) R.string.document_delete_title else R.string.delete_operation_title),
            description=stringResource(description),
            onDismiss=dismissDelete,onConfirm=confirmDelete
        )
    }
}
