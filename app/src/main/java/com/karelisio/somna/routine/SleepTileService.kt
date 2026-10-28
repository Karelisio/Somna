package com.karelisio.somna.routine

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.karelisio.somna.container
import com.karelisio.somna.core.SleepCalculator
import com.karelisio.somna.core.TimeFormat
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Settings tile "Je me couche": one tap sets the alarm at the end of the preferred cycles. */
class SleepTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        container.appScope.launch {
            val next = runCatching { container.alarms.next() }.getOrNull()
            withContext(Dispatchers.Main) {
                render(next?.let { "Réveil ${TimeFormat.clock(it.second.toLocalTime())}" } ?: "Réveil en fin de cycle")
            }
        }
    }

    override fun onClick() {
        super.onClick()
        container.appScope.launch {
            val s = container.settings.current()
            val option = SleepCalculator.wakeTimesFor(
                LocalDateTime.now(), s.cycleMinutes, s.fallAsleepMinutes, s.preferredCycles..s.preferredCycles,
            ).first()
            container.alarms.create(option.time.hour, option.time.minute)
            container.sleep.markBedtime()
            withContext(Dispatchers.Main) {
                render("Réveil ${TimeFormat.clock(option.time)}")
                Toast.makeText(
                    this@SleepTileService,
                    "Réveil à ${TimeFormat.clock(option.time)} · ${option.cycles} cycles · bonne nuit 🌙",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun render(subtitle: String) {
        val tile = qsTile ?: return
        tile.label = "Je me couche"
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.updateTile()
    }
}
