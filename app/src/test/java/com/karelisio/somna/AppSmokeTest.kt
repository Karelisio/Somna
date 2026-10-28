package com.karelisio.somna

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real activity: splash, theme, navigation, onboarding then the main tabs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle() {
        repeat(4) {
            compose.mainClock.advanceTimeBy(1_000)
            Thread.sleep(150)
            compose.waitForIdle()
        }
    }

    @Test
    fun `first launch shows the onboarding, then the calculator once done`() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Passer l'intro").assertExists()

        compose.onNodeWithText("Passer l'intro").performClick()
        settle()
        // One page per permission: skip ("Plus tard") or continue when already granted.
        repeat(12) {
            when {
                compose.onAllNodesWithText("Commencer").fetchSemanticsNodes().isNotEmpty() -> return@repeat
                compose.onAllNodesWithText("Continuer").fetchSemanticsNodes().isNotEmpty() ->
                    compose.onNodeWithText("Continuer").performClick()
                else -> compose.onNodeWithText("Plus tard").performClick()
            }
            settle()
        }
        compose.onNodeWithText("Commencer").performClick()
        settle()
        compose.onNodeWithText("Je me couche").assertExists()
        assertTrue(runBlocking { compose.activity.container.settings.current().onboardingDone })
        assertTrue(compose.onAllNodesWithText("Réveils").fetchSemanticsNodes().isNotEmpty())
    }
}
