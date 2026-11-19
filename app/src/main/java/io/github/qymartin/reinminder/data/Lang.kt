package io.github.qymartin.reinminder.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app's own language setting (中文 default, or English), independent of the phone's language. */
object Lang {
    fun code(context: Context): String = Prefs(context).lang

    fun isEnglish(context: Context): Boolean = code(context) == "en"

    fun locale(code: String): Locale = if (code == "en") Locale.US else Locale.SIMPLIFIED_CHINESE

    /** A context whose resources use the app language. Used by activities, notifications and receivers. */
    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale(code(context)))
        return context.createConfigurationContext(config)
    }
}
