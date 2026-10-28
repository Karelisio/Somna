package com.karelisio.somna.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.karelisio.somna.SomnaApp
import com.karelisio.somna.alarm.AlarmOutcome
import com.karelisio.somna.alarm.AlarmScreen
import com.karelisio.somna.alarm.AlarmSession
import com.karelisio.somna.alarm.AlarmSpec
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.Mission
import com.karelisio.somna.data.SleepEntry
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.SomnaViewModel
import com.karelisio.somna.ui.alarms.AlarmEditScreen
import com.karelisio.somna.ui.alarms.AlarmsScreen
import com.karelisio.somna.ui.home.CalculatorScreen
import com.karelisio.somna.ui.journal.JournalScreen
import com.karelisio.somna.ui.onboarding.OnboardingScreen
import com.karelisio.somna.ui.settings.BatteryGuideScreen
import com.karelisio.somna.ui.settings.PermissionsScreen
import com.karelisio.somna.ui.settings.SettingsScreen
import com.karelisio.somna.ui.sounds.SoundsScreen
import com.karelisio.somna.ui.theme.SomnaTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen to PNG (light and dark) for visual review, without an emulator.
 * Run with: ./gradlew testDebugUnitTest -Pscreenshots → build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir = System.getProperty("somna.screenshots.dir")?.let(::File)

    @Test
    fun captureScreens() {
        assumeTrue(outDir != null)
        outDir!!.mkdirs()
        val app = ApplicationProvider.getApplicationContext<SomnaApp>()
        val container = app.container
        runBlocking {
            container.settings.setOnboardingDone(true)
            container.alarms.create(6, 50)
            val weekdays = container.alarms.create(7, 30, days = AlarmEntity.WEEKDAYS, label = "Boulot")
            container.alarms.save(weekdays.copy(smartWakeMinutes = 25, mission = Mission.MATH.name))
            val weekend = container.alarms.create(9, 15, days = AlarmEntity.WEEKEND)
            container.alarms.setEnabled(weekend, false)
            val now = System.currentTimeMillis()
            val day = 24 * 3_600_000L
            listOf(470, 455, 512, 390, 480, 445, 530).forEachIndexed { i, minutes ->
                val wake = now - (i + 1) * day
                container.sleep.add(SleepEntry(bedtime = wake - minutes * 60_000L, wakeTime = wake, rating = listOf(4, 3, 5, 2, 4, 3, 5)[i]))
            }
        }
        val somna = SomnaViewModel(container)
        val spec = AlarmSpec(
            alarmId = 1, triggerAt = System.currentTimeMillis() + 4 * 60_000L, hour = 6, minute = 50,
            sound = "BIRDS", mission = Mission.MATH.name, sunriseMinutes = 10,
        )

        var dark by mutableStateOf(true)
        var screen by mutableStateOf<@Composable () -> Unit>({})
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSomna provides somna) {
                SomnaTheme(darkOverride = dark, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { screen() }
                }
            }
        }

        fun shot(name: String, settle: Long = 2_500, before: () -> Unit = {}, content: @Composable () -> Unit) {
            for (isDark in listOf(true, false)) {
                dark = isDark
                screen = content
                compose.mainClock.advanceTimeBy(settle)
                compose.waitForIdle()
                Thread.sleep(600)
                compose.mainClock.advanceTimeBy(settle)
                compose.waitForIdle()
                runCatching { before() }
                compose.mainClock.advanceTimeBy(settle)
                compose.waitForIdle()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                File(outDir, "$name-${if (isDark) "dark" else "light"}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }

        val padding = PaddingValues()
        shot("01-calculator") { CalculatorScreen(padding, 0, {}, {}, {}) }
        shot("02-alarms") { AlarmsScreen(padding, {}, {}, {}) }
        shot("03-alarm-edit") { AlarmEditScreen(2) {} }
        shot("04-sounds") { SoundsScreen(padding) {} }
        shot("05-journal") { JournalScreen(padding) {} }
        shot("06-settings") { SettingsScreen({}, {}, {}) }
        shot("07-permissions") { PermissionsScreen({}, {}) }
        shot("08-battery") { BatteryGuideScreen {} }
        shot("09-onboarding") { OnboardingScreen {} }
        shot("09b-onboarding-permission", before = {
            compose.onNodeWithText("Passer l'intro").performClick()
            compose.mainClock.advanceTimeBy(3_000)
        }) { OnboardingScreen {} }
        shot("10-alarm-sunrise") {
            AlarmScreen(AlarmSession(spec, sunriseStart = System.currentTimeMillis() - 6 * 60_000L), null, {}, {})
        }
        shot("11-alarm-ringing") {
            AlarmScreen(AlarmSession(spec, ringing = true, ringStartedAt = System.currentTimeMillis()), null, {}, {})
        }
        shot("12-alarm-rating") {
            AlarmScreen(null, AlarmOutcome.Dismissed(spec, System.currentTimeMillis()), {}, {})
        }
    }
}
