package io.github.korppujauho.pocketts

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import io.github.korppujauho.pocketts.ui.ConnectScreen
import io.github.korppujauho.pocketts.ui.PocketTsTheme
import org.junit.Assert.assertTrue
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
        val connect = compose.onNode(hasText(context.getString(R.string.connect)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val bottom = connect.assertIsDisplayed().assertIsEnabled().getUnclippedBoundsInRoot().bottom
        val nickname = compose.onNodeWithText("AlfredTS_VaseJmeno").getUnclippedBoundsInRoot()
        val address = compose.onNodeWithText("chochrun.eu").getUnclippedBoundsInRoot()
        assertTrue("Connect button must be above both fields", bottom <= nickname.top && bottom <= address.top)
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
