package com.carcare.analytics.feature.auth

import com.carcare.analytics.core.designsystem.localizedFieldError
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.FailureCard
import com.carcare.analytics.feature.insights.OverviewIcon

@Composable
fun AuthScreen(register: Boolean,onSwitch: () -> Unit,onLanguage: () -> Unit,viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        if (!state.busy) {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.submit(register)
        }
    }
    BackHandler(enabled=state.busy) { }

    @Composable fun error(key: String): String? =
        state.fields[key]?.let { stringResource(it) } ?: localizedFieldError(state.failure,key)

    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            contentAlignment=Alignment.TopCenter
        ) {
            val compact = maxHeight < 600.dp
            Column(
                Modifier.widthIn(max=460.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start=24.dp,end=24.dp,top=if(compact) 16.dp else 52.dp,bottom=20.dp)
            ) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { AuthBrand() }
                    val label=stringResource(R.string.app_language)
                    IconButton(
                        onClick={ focus.clearFocus(); keyboard?.hide(); onLanguage() },enabled=!state.busy,
                        modifier=Modifier.semantics { contentDescription=label }
                    ) { OverviewIcon("language",Modifier.size(23.dp)) }
                }
                Spacer(Modifier.height(if(compact) 19.dp else 34.dp))
                Text(
                    stringResource(if(register) R.string.register_title else R.string.login_title),
                    fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.9).sp
                )
                if (!register) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tagline),style=MaterialTheme.typography.bodyLarge,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(if(compact) 13.dp else 18.dp))
                    Box(
                        Modifier.width(36.dp).height(3.dp)
                            .background(MaterialTheme.colorScheme.primary,RoundedCornerShape(2.dp))
                    )
                }
                Spacer(Modifier.height(if(compact) 20.dp else 30.dp))

                Column(verticalArrangement=Arrangement.spacedBy(if(compact) 12.dp else 17.dp)) {
                    AuthField(
                        label=stringResource(R.string.email),
                        value=state.email,onChange={ viewModel.change("email",it) },
                        enabled=!state.busy,error=error("email"),
                        type=KeyboardType.Email,autofill=ContentType.Username + ContentType.EmailAddress,icon="mail",
                        placeholder=stringResource(R.string.auth_email_placeholder)
                    )
                    AuthField(
                        label=stringResource(R.string.password),
                        value=state.password,onChange={ viewModel.change("password",it) },
                        enabled=!state.busy,error=error("password"),
                        type=KeyboardType.Password,autofill=if(register) ContentType.NewPassword else ContentType.Password,
                        icon="lock",hint=if(register) stringResource(R.string.auth_password_hint) else null,
                        onDone=if(register) null else submit
                    )
                    if (register) AuthField(
                        label=stringResource(R.string.confirm_password),
                        value=state.confirmation,onChange={ viewModel.change("confirmation",it) },
                        enabled=!state.busy,error=error("confirmation"),
                        type=KeyboardType.Password,autofill=ContentType.NewPassword,icon="lock",onDone=submit
                    )
                }

                Spacer(Modifier.height(if(compact) 16.dp else if(register) 28.dp else 40.dp))
                state.failure?.takeIf { it.fields.isEmpty() || it.fields.keys.any { field ->
                    field!="email" && field!="password" && (field!="confirmation" || !register)
                } }?.let {
                    Box(Modifier.fillMaxWidth().semantics { liveRegion=LiveRegionMode.Polite }) { FailureCard(it) }
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick=submit,enabled=!state.busy,shape=RoundedCornerShape(16.dp),
                    modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp),color=LocalContentColor.current,strokeWidth=2.dp)
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(
                        stringResource(if(state.busy) R.string.auth_busy else if(register) R.string.register else R.string.login),
                        style=MaterialTheme.typography.titleMedium
                    )
                }
                TextButton(
                    onClick={ focus.clearFocus(); keyboard?.hide(); onSwitch() },
                    enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth().padding(top=6.dp)
                ) {
                    Text(stringResource(if(register) R.string.have_account else R.string.need_account),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun AuthBrand() {
    Row(
        horizontalArrangement=Arrangement.spacedBy(12.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(width=78.dp,height=58.dp),contentAlignment=Alignment.Center) {
                OverviewIcon("car",Modifier.size(54.dp))
            }
        }
        Text("CarCare",fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
    }
}

@Composable
private fun AuthField(
    label: String,value: String,onChange: (String) -> Unit,enabled: Boolean,error: String?,
    type: KeyboardType,autofill: ContentType,icon: String,placeholder: String? = null,
    hint: String? = null,onDone: (() -> Unit)? = null
) {
    val secret = type==KeyboardType.Password
    var visible by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,Modifier.clearAndSetSemantics { },style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        OutlinedTextField(
            value=value,onValueChange=onChange,enabled=enabled,singleLine=true,isError=error!=null,
            shape=RoundedCornerShape(14.dp),textStyle=MaterialTheme.typography.bodyLarge,
            colors=OutlinedTextFieldDefaults.colors(
                focusedContainerColor=colors.surfaceContainerLow,unfocusedContainerColor=colors.surfaceContainerLow,
                unfocusedBorderColor=colors.outlineVariant
            ),
            placeholder=placeholder?.let { { Text(it,color=colors.onSurfaceVariant) } },
            leadingIcon={ OverviewIcon(icon,Modifier.size(23.dp)) },
            trailingIcon=if(secret) {
                {
                    val toggle = stringResource(if(visible) R.string.hide_password else R.string.show_password,label)
                    IconButton(
                        onClick={ visible=!visible },enabled=enabled,modifier=Modifier.semantics { contentDescription=toggle }
                    ) {
                        OverviewIcon(if(visible) "eyeOff" else "eye",Modifier.size(22.dp),LocalContentColor.current)
                    }
                }
            } else null,
            visualTransformation=if(secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions=KeyboardOptions(keyboardType=type,imeAction=if(onDone==null) ImeAction.Next else ImeAction.Done),
            keyboardActions=KeyboardActions(onDone=onDone?.let { { it() } }),
            modifier=Modifier.fillMaxWidth().semantics {
                contentDescription=label
                contentType=autofill
                if(secret) password()
            }
        )
        val support = error ?: hint
        if(support!=null) Text(
            support,modifier=Modifier.semantics { if(error!=null) liveRegion=LiveRegionMode.Polite },
            style=MaterialTheme.typography.bodySmall,color=if(error!=null) colors.error else colors.onSurfaceVariant
        )
    }
}
