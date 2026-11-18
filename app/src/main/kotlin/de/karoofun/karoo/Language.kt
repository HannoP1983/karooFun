package de.karoofun.karoo

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Context whose resources use the language chosen in the app settings, or this context unchanged when it is
 * set to the system language. Only what the app draws follows it: the field names in the Karoo's own field
 * list come from the Karoo app, which always uses the Karoo's system language.
 */
fun Context.localized(): Context {
    val language = FunSettings.load(this).language
    if (language == FunSettings.LANGUAGE_AUTO) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale(language)) })
}

/** Language of this context's resources, e.g. "de". */
@Suppress("DEPRECATION")
val Context.language: String get() = resources.configuration.locale.language
