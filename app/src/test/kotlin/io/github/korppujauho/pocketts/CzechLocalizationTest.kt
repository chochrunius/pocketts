package io.github.korppujauho.pocketts

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "cs-rCZ")
class CzechLocalizationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun czechPhoneUsesCzechResourcesAndKeepsProductNames() {
        assertEquals("Čeština", AppLanguage.choices.single { it.first == "cs" }.second)
        assertEquals("Nastavení", context.getString(R.string.settings))
        assertEquals("Zapamatovat poslední kanál", context.getString(R.string.remember_last_channel))
        assertEquals("Okamžitý přesun", context.getString(R.string.initial_channel_instant))
        assertEquals("AlfredTS", context.getString(R.string.app_name))
        assertEquals("Opus Voice", context.getString(R.string.channel_codec_voice))
    }

    @Test fun androidPerAppCzechRegionIsRecognizedInLanguagePicker() {
        val manager = context.getSystemService(LocaleManager::class.java)
        val previous = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList.forLanguageTags("cs-CZ")
            assertEquals("cs", AppLanguage.current(context))
            manager.applicationLocales = LocaleList.getEmptyLocaleList()
            assertEquals("", AppLanguage.current(context))
        } finally {
            manager.applicationLocales = previous
        }
    }

    @Test fun czechIntegerPluralsUseOneFewAndOtherIncludingLargeCounts() {
        val cases = mapOf(0 to "0 uživatelů", 1 to "1 uživatel", 2 to "2 uživatelé",
            4 to "4 uživatelé", 5 to "5 uživatelů", 11 to "11 uživatelů",
            21 to "21 uživatelů", 22 to "22 uživatelů", 101 to "101 uživatelů")
        for ((count, expected) in cases) {
            assertEquals(expected, context.resources.getQuantityString(R.plurals.channel_people, count, count))
        }
        assertEquals("asi minutu", context.resources.getQuantityString(R.plurals.about_minutes, 1, 1))
        assertEquals("asi 3 minuty", context.resources.getQuantityString(R.plurals.about_minutes, 3, 3))
        assertEquals("asi 5 minut", context.resources.getQuantityString(R.plurals.about_minutes, 5, 5))
        assertEquals("Obrázek 2 z 3 nebyl odeslán: chyba",
            context.getString(R.string.pictures_not_sent, 2, 3, "chyba"))
    }

    @Test @Config(sdk = [29]) fun olderAndroidUsesSavedCzechAndUnsupportedLocaleFallsBackToEnglish() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val previous = prefs.getString("language", null)
        try {
            prefs.edit().putString("language", "cs").commit()
            assertEquals("Nastavení", AppLanguage.wrap(context).getString(R.string.settings))
            assertEquals("cs", AppLanguage.current(context))
            val config = Configuration(context.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags("ja-JP"))
            assertEquals("Settings", context.createConfigurationContext(config).getString(R.string.settings))
        } finally {
            prefs.edit().putString("language", previous).commit()
        }
    }
}
