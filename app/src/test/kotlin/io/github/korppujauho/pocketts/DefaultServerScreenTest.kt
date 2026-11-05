package io.github.korppujauho.pocketts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import io.github.korppujauho.pocketts.ui.ConnectScreen
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
class DefaultServerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstRenderUsesStoredFavoriteAndConnectionDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val settings = Settings(context)
        settings.initializeDefaults()
        compose.setContent {
            PocketTsTheme {
                ConnectScreen(settings.nickname, settings.address, settings.serverPassword,
                    Favorites.load(context), null, Example.noConnect)
            }
        }
        compose.onNodeWithText("AlfredTS_VaseJmeno").assertExists()
        compose.onNodeWithText("chochrun.eu").assertExists()
        compose.onNodeWithText("Ludmila Chochrun.eu").assertExists()
        compose.onNodeWithText(context.getString(R.string.favourites_in)).assertExists()
        compose.onNode(hasText(context.getString(R.string.favourites_in))).onChildren()
            .filter(isToggleable()).onFirst().assertIsOn().assertIsNotEnabled()
        compose.onNode(hasText(context.getString(R.string.remember_last_channel))).onChildren()
            .filter(isToggleable()).onFirst().assertIsOn()
        compose.onNode(hasText(context.getString(R.string.remember_password))).onChildren()
            .filter(isToggleable()).onFirst().assertIsOff()
    }
}
