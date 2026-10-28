package com.karelisio.somna

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.SomnaViewModel
import com.karelisio.somna.ui.settings.SettingsScreen
import com.karelisio.somna.ui.theme.SomnaTheme
import com.karelisio.somna.ui.update.UpdateSheetHost
import com.karelisio.somna.update.ReleaseInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A newer release found from the settings screen must open the install sheet right there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h4000dp-mdpi")
class UpdateFromSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test(timeout = 180_000)
    fun `settings offer to install an available update`() {
        val app = ApplicationProvider.getApplicationContext<SomnaApp>()
        val release = ReleaseInfo("9.9.9", "v9.9.9", "Somna 9.9.9", "- Nouveauté de test", "https://example.invalid/somna.apk", 1L, "")
        runBlocking {
            app.container.settings.setOnboardingDone(true)
            app.container.settings.setUpdateCheckResult(System.currentTimeMillis(), Json.encodeToString(ReleaseInfo.serializer(), release))
        }
        val somna = SomnaViewModel(app.container).apply { updateSheetDismissed = true }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSomna provides somna) {
                SomnaTheme(darkOverride = true, dynamicColor = false) {
                    SettingsScreen({}, {}, {})
                    UpdateSheetHost()
                }
            }
        }
        repeat(4) {
            compose.mainClock.advanceTimeBy(1_000)
            Thread.sleep(150)
            compose.waitForIdle()
        }
        compose.onNodeWithText("Mettre à jour").performClick()
        repeat(4) {
            compose.mainClock.advanceTimeBy(1_000)
            Thread.sleep(200)
            compose.waitForIdle()
        }
        compose.onNodeWithText("Somna 9.9.9").assertExists()
        compose.onNodeWithText("Nouveautés").assertExists()
    }
}
