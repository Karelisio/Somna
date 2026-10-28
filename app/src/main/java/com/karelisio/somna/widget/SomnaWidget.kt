package com.karelisio.somna.widget

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.somna.MainActivity
import com.karelisio.somna.R
import com.karelisio.somna.container
import com.karelisio.somna.core.SleepCalculator
import com.karelisio.somna.core.SleepOption
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.data.SomnaSettings
import com.karelisio.somna.ui.theme.NightDarkScheme
import com.karelisio.somna.ui.theme.NightLightScheme
import java.time.LocalDateTime

private val FallbackColors = ColorProviders(light = NightLightScheme, dark = NightDarkScheme)

/** Home-screen widget: "Si tu te couches maintenant → réveil à 6h50 · 8h20". */
class SomnaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = runCatching { context.container.settings.current() }.getOrDefault(SomnaSettings())
        val options = SleepCalculator.wakeTimesFor(LocalDateTime.now(), settings.cycleMinutes, settings.fallAsleepMinutes, 5..6)
        val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        provideContent {
            GlanceTheme(colors = if (dynamic) GlanceTheme.colors else FallbackColors) {
                WidgetContent(options)
            }
        }
    }

    @Composable
    private fun WidgetContent(options: List<SleepOption>) {
        val size = LocalSize.current
        val compact = size.width < MEDIUM.width
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .clickable(actionStartActivity<MainActivity>(actionParametersOf(destinationKey to MainActivity.DEST_SLEEP_NOW))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_notification),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.primary),
                    modifier = GlanceModifier.size(18.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = if (compact) "Couché maintenant" else "Si tu te couches maintenant",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!compact) {
                    Text("→ réveil à ", style = TextStyle(color = colors.onSurface, fontSize = 16.sp))
                }
                options.forEachIndexed { index, option ->
                    if (index > 0) Text(" · ", style = TextStyle(color = colors.outline, fontSize = 22.sp))
                    val time = option.time
                    Text(
                        text = TimeFormat.short(time),
                        style = TextStyle(
                            color = if (option.ideal) colors.primary else colors.onSurface,
                            fontSize = if (compact) 22.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier.clickable(
                            actionRunCallback<CreateAlarmAction>(
                                actionParametersOf(CreateAlarmAction.hourKey to time.hour, CreateAlarmAction.minuteKey to time.minute),
                            ),
                        ),
                    )
                }
            }
            if (size.height >= LARGE.height) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "Touchez une heure pour régler le réveil",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 60.dp)
        private val MEDIUM = DpSize(220.dp, 70.dp)
        private val LARGE = DpSize(260.dp, 110.dp)
        val destinationKey = ActionParameters.Key<String>(MainActivity.EXTRA_DESTINATION)

        suspend fun refresh(context: Context) {
            runCatching { SomnaWidget().updateAll(context) }
        }
    }
}

/** Tapping a time on the widget sets the alarm directly. */
class CreateAlarmAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val hour = parameters[hourKey] ?: return
        val minute = parameters[minuteKey] ?: return
        val container = context.container
        container.alarms.create(hour, minute)
        container.sleep.markBedtime()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Réveil réglé à ${"%02d:%02d".format(hour, minute)} · bonne nuit 🌙", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        val hourKey = ActionParameters.Key<Int>("hour")
        val minuteKey = ActionParameters.Key<Int>("minute")
    }
}
