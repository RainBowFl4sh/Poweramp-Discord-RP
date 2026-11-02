package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import java.util.Locale

/**
 * One language of the app.
 * [tag]: BCP-47 tag (language picker, LocaleManager, res/xml/locales_config.xml).
 * [qualifier]: resource folder suffix ("" for the English default, "pt" for values-pt / raw-pt, "b+sr+Latn", ...).
 * [englishName]: for docs and tests. [autonym]: the language's name in itself, shown in the picker.
 * [aiTranslation]: machine translated (the About tab says so and asks for corrections).
 */
data class AppLanguage(
    val tag: String,
    val qualifier: String,
    val englishName: String,
    val autonym: String,
    val aiTranslation: Boolean,
) {
    /** "values", "values-de", "values-b+sr+Latn", ... */
    val valuesFolder: String get() = folder("values")

    /** "raw", "raw-de", ... (the in-app changelog) */
    val rawFolder: String get() = folder("raw")

    val locale: Locale get() = Locale.forLanguageTag(tag)

    private fun folder(type: String) = if (qualifier.isEmpty()) type else "$type-$qualifier"
}

/** The single list of the app's languages, in the order of the picker. */
object AppLanguages {
    val ALL: List<AppLanguage> = listOf(
        AppLanguage("en", "", "English", "English", aiTranslation = false),
        AppLanguage("de", "de", "German", "Deutsch", aiTranslation = false),
        ai("ru", "ru", "Russian", "Русский"),
        ai("uk", "uk", "Ukrainian", "Українська"),
        ai("fr", "fr", "French", "Français"),
        ai("es", "es", "Spanish", "Español"),
        ai("pt-BR", "pt", "Portuguese (Brazil)", "Português (Brasil)"),
        ai("it", "it", "Italian", "Italiano"),
        ai("nl", "nl", "Dutch", "Nederlands"),
        ai("da", "da", "Danish", "Dansk"),
        ai("sv", "sv", "Swedish", "Svenska"),
        ai("nb", "nb", "Norwegian Bokmål", "Norsk bokmål"),
        ai("fi", "fi", "Finnish", "Suomi"),
        ai("pl", "pl", "Polish", "Polski"),
        ai("cs", "cs", "Czech", "Čeština"),
        ai("hu", "hu", "Hungarian", "Magyar"),
        ai("ro", "ro", "Romanian", "Română"),
        ai("el", "el", "Greek", "Ελληνικά"),
        ai("tr", "tr", "Turkish", "Türkçe"),
        ai("sr", "sr", "Serbian (Cyrillic)", "Српски"),
        ai("sr-Latn", "b+sr+Latn", "Serbian (Latin)", "Srpski (latinica)"),
        ai("ar", "ar", "Arabic", "العربية"),
        ai("hi", "hi", "Hindi", "हिन्दी"),
        ai("id", "in", "Indonesian", "Bahasa Indonesia"),
        ai("vi", "vi", "Vietnamese", "Tiếng Việt"),
        ai("th", "th", "Thai", "ไทย"),
        ai("zh-CN", "zh-rCN", "Chinese (Simplified)", "简体中文"),
        ai("zh-TW", "zh-rTW", "Chinese (Traditional, Taiwan)", "繁體中文"),
        ai("ja", "ja", "Japanese", "日本語"),
        ai("ko", "ko", "Korean", "한국어"),
    )

    val TAGS: List<String> get() = ALL.map { it.tag }

    private fun ai(tag: String, qualifier: String, englishName: String, autonym: String) =
        AppLanguage(tag, qualifier, englishName, autonym, aiTranslation = true)

    fun byTag(tag: String): AppLanguage? = ALL.firstOrNull { it.tag == tag }

    /**
     * The app language for a locale tag (one the system stored, or a saved choice): the same tag, else one of the same
     * language - with the same script, else the same region, else the plain one. Null: not an app language.
     */
    fun match(tag: String?): AppLanguage? {
        if (tag.isNullOrBlank()) return null
        ALL.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it }
        val wanted = Locale.forLanguageTag(tag)
        val language = normalizedLanguage(wanted.language)
        val sameLanguage = ALL.filter { normalizedLanguage(it.locale.language) == language }
        if (language.isEmpty() || sameLanguage.isEmpty()) return null
        val script = scriptOf(wanted)
        return sameLanguage.firstOrNull { script.isNotEmpty() && scriptOf(it.locale).equals(script, ignoreCase = true) }
            ?: sameLanguage.firstOrNull { wanted.country.isNotEmpty() && it.locale.country.equals(wanted.country, ignoreCase = true) }
            ?: sameLanguage.firstOrNull { it.locale.script.isEmpty() }
            ?: sameLanguage.first()
    }

    /** Java may report Indonesian with the old code "in". */
    private fun normalizedLanguage(language: String) = if (language == "in") "id" else language.lowercase(Locale.ROOT)

    /** The written script; for Chinese implied by the region (Taiwan, Hong Kong, Macau: traditional, else simplified). */
    private fun scriptOf(locale: Locale): String = when {
        locale.script.isNotEmpty() -> locale.script
        locale.language != "zh" || locale.country.isEmpty() -> ""
        locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO") -> "Hant"
        else -> "Hans"
    }
}
