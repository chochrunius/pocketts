package io.github.korppujauho.pocketts

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.korppujauho.pocketts.proto.ServerModel
import io.github.korppujauho.pocketts.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelPositionTest {
    @get:Rule val compose = createComposeRule()
    private fun tree(withSelf: Boolean = true, crowded: Boolean = false) = flattenTree((1..50).map { id ->
        val people = if (id == 40) {
            (if (crowded) (1..25).map { ServerModel.Client(it, 40, "Other $it") } else emptyList()) +
                if (withSelf) listOf(ServerModel.Client(99, 40, "Local user")) else emptyList()
        } else emptyList()
        // Intentionally identical names: positioning must use IDs.
        ServerModel.Node(ServerModel.Channel(id, 0, id - 1, "Channel"), people, emptyList())
    })
    @Test fun delayedTreeThenManualScrollAndReconnect() {
        var rows by mutableStateOf(emptyList<ChannelItem>())
        var position by mutableStateOf(InitialChannelPosition())
        var connected by mutableStateOf(false)
        var ownId by mutableStateOf(0)
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            list = rememberLazyListState()
            PocketTsTheme {
                ChannelTree(rows, TreeContext(ownId, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp), listState = list, initialPosition = position, connected = connected, animated = false)
            }
        }
        compose.runOnIdle { rows = tree(withSelf = false) }
        compose.runOnIdle { assertTrue(position.pending); assertEquals(0, list.firstVisibleItemIndex) }
        compose.runOnIdle { rows = tree() }
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemIndex); connected = true; ownId = 99 }
        compose.onNodeWithText("Local user").assertIsDisplayed()
        compose.runOnIdle { assertFalse(position.pending) }
        compose.runOnIdle { runBlocking { list.scrollToItem(0) } }
        compose.runOnIdle { rows = tree().map { it.copy(depth = 1) } }
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemIndex) }
        compose.runOnIdle { position = InitialChannelPosition() }
        compose.onNodeWithText("Local user").assertIsDisplayed()
    }
    @Test fun ownClientVisibleInsideOversizedChannel() {
        compose.setContent {
            PocketTsTheme {
                ChannelTree(tree(crowded = true), TreeContext(99, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp), animated = false)
            }
        }
        compose.onNodeWithText("Local user").assertIsDisplayed()
    }
    @Test fun manualDragBeforeTreeLoadsCancelsInitialPosition() {
        var rows by mutableStateOf(tree(withSelf = false))
        val position = InitialChannelPosition()
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            list = rememberLazyListState()
            PocketTsTheme {
                ChannelTree(rows, TreeContext(99, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp).testTag("tree"), listState = list, initialPosition = position, animated = false)
            }
        }
        compose.onNodeWithTag("tree").performTouchInput { swipeUp() }
        compose.runOnIdle { assertFalse(position.pending) }
        var before = 0
        compose.runOnIdle { before = list.firstVisibleItemIndex; rows = tree() }
        compose.runOnIdle { assertEquals(before, list.firstVisibleItemIndex) }
    }

    @Test fun updatesDuringAnimationDoNotRestartOrCancelPositioning() {
        compose.mainClock.autoAdvance = false
        var rows by mutableStateOf(tree(crowded = true))
        val position = InitialChannelPosition()
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            list = rememberLazyListState()
            PocketTsTheme {
                ChannelTree(rows, TreeContext(99, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp).testTag("tree"), listState = list, initialPosition = position)
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertTrue(position.pending); assertEquals(0, list.firstVisibleItemIndex) }
        // Keep changing the tree across the measurement and animation boundaries.
        repeat(40) { n ->
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle { rows = rows.map { it.copy(node = it.node.copy(channel = it.node.channel.copy(topic = "$n"))) } }
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Local user").assertIsDisplayed()
        compose.runOnIdle { assertFalse(position.pending) }
    }

    @Test fun manualDragDuringAnimationCancelsWithoutProgrammaticSelfCancellation() {
        compose.mainClock.autoAdvance = false
        val position = InitialChannelPosition()
        compose.setContent {
            PocketTsTheme {
                ChannelTree(tree(), TreeContext(99, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp).testTag("tree"), initialPosition = position)
            }
        }
        compose.mainClock.advanceTimeBy(2400)
        compose.runOnIdle { assertTrue(position.pending) }
        compose.onNodeWithTag("tree").performTouchInput { swipeDown() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertFalse(position.pending) }
    }

    @Test fun dragDuringInitialWaitCancelsAnimation() {
        val position = InitialChannelPosition()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PocketTsTheme {
                ChannelTree(tree(), TreeContext(99, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp).testTag("tree"), initialPosition = position)
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("tree").performTouchInput { swipeUp() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertFalse(position.pending) }
    }

    @Test fun screenTransitionDuringAnimationResumesAndDoesNotRepeatAfterCompletion() {
        var shown by mutableStateOf(true)
        val position = InitialChannelPosition()
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            list = rememberLazyListState()
            PocketTsTheme {
                if (shown) ChannelTree(tree(), TreeContext(99, 40, emptySet(), canTalk = { true }), {}, {},
                    Modifier.fillMaxWidth().height(300.dp), listState = list, initialPosition = position)
            }
        }
        compose.mainClock.advanceTimeBy(2400)
        compose.runOnIdle { shown = false }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(position.pending); shown = true }
        // Android layout callbacks need idle/layout turns as well as virtual Compose frames.
        repeat(60) {
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle { }
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Local user").assertIsDisplayed()
        compose.runOnIdle { assertFalse(position.pending); runBlocking { list.scrollToItem(0) }; shown = false }
        compose.runOnIdle { shown = true }
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemIndex) }
    }
}
