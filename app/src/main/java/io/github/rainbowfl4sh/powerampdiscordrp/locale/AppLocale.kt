package io.github.rainbowfl4sh.powerampdiscordrp.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguages
import io.github.rainbowfl4sh.powerampdiscordrp.ui.theme.AppearanceStore
import java.util.Locale

/**
 * The app's own language (null: the system's).
 *
 * Android 13+: kept by the system (LocaleManager - also Settings -> Apps -> Language); it gives every context of the
 * app that language by itself. Android 8-12: kept in the "ui" preferences (like the theme: not part of the settings
 * export) and applied where texts are made - [wrap] for the activity, [resources] for the service, the notifications
 * and other application-context texts.
 */
object AppLocale {
    const val KEY_LANGUAGE = "language"

    /** The chosen language, or null for the system default. */
    fun current(context: Context, sdk: Int = Build.VERSION.SDK_INT): AppLanguage? =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) systemChoice(context)
        else AppLanguages.match(prefs(context).getString(KEY_LANGUAGE, null))

    /**
     * Chooses [language] (null: system default). Android 13+ recreates the activities by itself; before that the
     * caller recreates its activity (true returned).
     */
    fun set(context: Context, language: AppLanguage?, sdk: Int = Build.VERSION.SDK_INT): Boolean {
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            setSystemChoice(context, language)
            return false
        }
        prefs(context).edit().apply { if (language == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, language.tag) }.apply()
        cache = null
        Locale.setDefault(language?.locale ?: systemLocale())
        return true
    }

    /**
     * Android 8-12: [base] with the chosen language (for Activity.attachBaseContext); also the default locale for
     * dates and numbers. Unchanged on Android 13+ or without a choice.
     */
    fun wrap(base: Context, sdk: Int = Build.VERSION.SDK_INT): Context {
        val language = storedChoice(base, sdk) ?: return base
        Locale.setDefault(language.locale)
        return base.createConfigurationContext(configuration(base, language))
    }

    /** Resources in the app's language for texts made outside an activity (notifications, the engine's words). */
    fun resources(context: Context, sdk: Int = Build.VERSION.SDK_INT): Resources {
        val language = storedChoice(context, sdk) ?: return context.resources
        cache?.let { (tag, res) -> if (tag == language.tag) return res }
        val res = context.applicationContext.createConfigurationContext(configuration(context, language)).resources
        cache = language.tag to res
        return res
    }

    /** Android 13+: a choice saved on Android 12 or older (before a system update) is handed to the system once. */
    fun migrate(context: Context, sdk: Int = Build.VERSION.SDK_INT) {
        if (sdk < Build.VERSION_CODES.TIRAMISU) return
        val prefs = prefs(context)
        val saved = prefs.getString(KEY_LANGUAGE, null) ?: return
        val language = AppLanguages.match(saved)
        if (language != null && systemChoice(context) == null) setSystemChoice(context, language)
        prefs.edit().remove(KEY_LANGUAGE).apply()
    }

    @Volatile
    private var cache: Pair<String, Resources>? = null

    private fun storedChoice(context: Context, sdk: Int): AppLanguage? =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) null else current(context, sdk)

    private fun configuration(context: Context, language: AppLanguage) =
        Configuration(context.resources.configuration).apply { setLocales(LocaleList(language.locale)) }   // also RTL

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppearanceStore.PREFS_NAME, Context.MODE_PRIVATE)

    private fun localeManager(context: Context): LocaleManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.getSystemService(LocaleManager::class.java) else null

    private fun systemChoice(context: Context): AppLanguage? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = localeManager(context)?.applicationLocales ?: return null
        return if (locales.isEmpty) null else AppLanguages.match(locales[0].toLanguageTag())
    }

    private fun setSystemChoice(context: Context, language: AppLanguage?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        localeManager(context)?.applicationLocales =
            if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
    }
}
