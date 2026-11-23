package com.carcare.analytics.core.designsystem

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.carcare.analytics.R
import java.util.Locale

enum class AppLanguage(val tag: String,val displayName: String) {
    POLISH("pl-PL","Polski"), ENGLISH("en-GB","English");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun applyTo(context: Context): Context {
        val configuration=Configuration(context.resources.configuration).apply { setLocale(this@AppLanguage.locale) }
        return context.createConfigurationContext(configuration)
    }

    companion object {
        private const val PREFS="carcare_ui"
        private const val LANGUAGE="language"

        fun read(context: Context): AppLanguage {
            val tag=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(LANGUAGE,null)
            return entries.firstOrNull { it.tag==tag } ?: POLISH
        }

        fun save(context: Context,language: AppLanguage) {
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(LANGUAGE,language.tag).apply()
        }
    }
}

@Composable
internal fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun LanguageSettingsDialog(selected: AppLanguage,onSelect: (AppLanguage) -> Unit,onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text(stringResource(R.string.app_language)) },
        text={
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min=48.dp)
                            .selectable(selected==language,role=Role.RadioButton,onClick={ onSelect(language) }),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        RadioButton(selected=selected==language,onClick=null)
                        Spacer(Modifier.width(12.dp))
                        Text(language.displayName,style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.close_dialog)) } }
    )
}
