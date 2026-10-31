package io.github.korppujauho.pocketts

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InitialPositionSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun freshAndExistingInstallationsDefaultToAnimated() {
        assertTrue(VoiceSettings().animateInitialChannel)
        val p = context.getSharedPreferences("voice", 0)
        p.edit().clear().commit()
        assertTrue(VoiceSettings.load(context).animateInitialChannel)
        p.edit().putBoolean("keepScreenOn", true).commit()
        assertTrue(VoiceSettings.load(context).animateInitialChannel)
    }
    @Test fun explicitInstantSurvivesReloadAndOtherSettingsChanges() {
        VoiceSettings(animateInitialChannel = false).save(context)
        val reopened = VoiceSettings.load(context)
        assertFalse(reopened.animateInitialChannel)
        reopened.copy(keepScreenOn = true).save(context)
        assertFalse(VoiceSettings.load(context).animateInitialChannel)
        reopened.copy(animateInitialChannel = true).save(context)
        assertTrue(VoiceSettings.load(context).animateInitialChannel)
    }
}
