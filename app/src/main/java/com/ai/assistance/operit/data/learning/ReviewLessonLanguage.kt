package com.ai.assistance.operit.data.learning

/**
 * B02: decides the language a generated lesson is written in.
 *
 * The scheduler used to pass `useEnglish = true` unconditionally, so a Chinese-locale user got
 * English lessons. The decision is derived from the same signal ConversationService uses
 * (`LocaleUtils.usesChineseContent`), but is expressed here as a pure function of the resolved
 * language code so it can be unit-tested on the JVM: LocaleUtils needs a real Android Context
 * and this project has no Robolectric.
 *
 * Limitation (B02): the generation language is NOT persisted with the lesson. [TaskEpisode] and
 * its ObjectBox entity `TaskEpisodeEntity` have no `language` column, and adding one would change
 * the ObjectBox schema and require regenerating `app/objectbox-models/default.json`, which is out
 * of scope for this change. The language therefore only shapes the Reflector request
 * (`ReflectorCurator.reflect(useEnglish = ...)`) and is not recorded on the stored episode.
 * Recording it is a follow-up that requires a model regeneration.
 */
object ReviewLessonLanguage {

    /** Language code recorded for a lesson generated in Chinese. */
    const val LANGUAGE_CHINESE = "zh"

    /** Language code recorded for a lesson generated in English. */
    const val LANGUAGE_ENGLISH = "en"

    /**
     * True when the resolved language code selects Chinese content. Mirrors
     * LocaleUtils.usesChineseContent: the code is lower-cased with Locale.ROOT and matched by
     * prefix, so "zh", "zh-CN" and "zh-Hans" all select Chinese.
     */
    fun usesChineseContent(languageCode: String): Boolean =
        languageCode.lowercase(java.util.Locale.ROOT).startsWith(LANGUAGE_CHINESE)

    /**
     * The language a lesson must be generated in for the given resolved language code.
     * Chinese content -> Chinese; every other supported language -> English, which is the
     * same binary choice ConversationService makes.
     */
    fun lessonLanguageFor(languageCode: String): String =
        if (usesChineseContent(languageCode)) LANGUAGE_CHINESE else LANGUAGE_ENGLISH

    /** True when the lesson must be requested in English. */
    fun useEnglishFor(languageCode: String): Boolean = !usesChineseContent(languageCode)
}
