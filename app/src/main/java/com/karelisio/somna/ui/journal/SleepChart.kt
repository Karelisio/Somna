package com.karelisio.somna.ui.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.data.SleepStats
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bars of sleep duration per night, growing in cascade, with the target duration as a dashed line. */
@Composable
fun SleepChart(points: List<SleepStats.DayPoint>, days: Int, targetMinutes: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val byDate = points.associateBy { it.date }
    val animations = remember(days) { List(days) { Animatable(0f) } }
    LaunchedEffect(days, points) {
        animations.forEachIndexed { i, a ->
            launch {
                delay(i * (if (days > 7) 12L else 50L))
                a.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.outline
    val maxMinutes = maxOf(targetMinutes + 90, points.maxOfOrNull { it.minutes.toInt() } ?: 0).toFloat()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val gap = if (days > 7) 3.dp.toPx() else 10.dp.toPx()
            val barWidth = (size.width - gap * (days - 1)) / days
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            dates.forEachIndexed { i, date ->
                val x = i * (barWidth + gap)
                drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), radius, alpha = 0.5f)
                val point = byDate[date] ?: return@forEachIndexed
                val h = size.height * (point.minutes / maxMinutes).coerceIn(0.04f, 1f) * animations[i].value
                val color = if (point.minutes >= targetMinutes - 15) primary else tertiary
                drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), radius)
            }
            val y = size.height * (1f - targetMinutes / maxMinutes)
            drawLine(
                outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
        if (days <= 7) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                dates.forEach {
                    Text(
                        TimeFormat.dayName(it).take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
