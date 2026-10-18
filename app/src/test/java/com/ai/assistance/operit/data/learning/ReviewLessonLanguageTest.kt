package com.ai.assistance.operit.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for B02: the lesson generation language must follow the user's locale
 * instead of being hardcoded to English. [ReviewLessonLanguage] is pure (no Android Context, no
 * ObjectBox), so it runs on the JVM without Robolectric.
 */
class ReviewLessonLanguageTest {

    // --- B02 acceptance criterion: a Chinese-locale user receives Chinese lessons ---

    @Test
    fun chineseLocale_requestsChinese() {
        // B02: a Chinese-locale user must NOT be served English lessons.
        assertFalse(ReviewLessonLanguage.useEnglishFor("zh"))
        assertEquals("zh", ReviewLessonLanguage.lessonLanguageFor("zh"))
    }

    @Test
    fun chineseRegionVariant_requestsChinese() {
        // B02: region/script variants of Chinese must also select Chinese lessons.
        assertFalse(ReviewLessonLanguage.useEnglishFor("zh-CN"))
        assertFalse(ReviewLessonLanguage.useEnglishFor("zh-Hans"))
    }

    @Test
    fun englishLocale_requestsEnglish() {
        // B02: an English-locale user still receives English lessons.
        assertTrue(ReviewLessonLanguage.useEnglishFor("en"))
        assertEquals("en", ReviewLessonLanguage.lessonLanguageFor("en"))
    }

    @Test
    fun otherSupportedLocale_requestsEnglish() {
        // B02: every non-Chinese supported locale takes the same binary English branch that
        // ConversationService takes.
        assertTrue(ReviewLessonLanguage.useEnglishFor("ja"))
        assertTrue(ReviewLessonLanguage.useEnglishFor("pt-BR"))
    }

    @Test
    fun uppercaseChineseCode_isStillChinese() {
        // B02: the code is lower-cased with Locale.ROOT before matching, so "ZH" is Chinese.
        assertFalse(ReviewLessonLanguage.useEnglishFor("ZH"))
    }

    @Test
    fun blankCode_requestsEnglish() {
        // B02 documented behaviour: an unresolved/blank code is not Chinese, so it selects the
        // English branch. This is asserted explicitly rather than left implicit.
        assertTrue(ReviewLessonLanguage.useEnglishFor(""))
    }

    @Test
    fun lessonLanguage_matchesUseEnglish() {
        // B02: lessonLanguageFor and useEnglishFor must never disagree - "zh" iff not English.
        val codes = listOf("zh", "zh-CN", "en", "ja", "")
        for (code in codes) {
            val expected = if (ReviewLessonLanguage.useEnglishFor(code)) "en" else "zh"
            assertEquals("code=$code", expected, ReviewLessonLanguage.lessonLanguageFor(code))
        }
    }
}
