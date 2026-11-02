package com.karelisio.somna.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.karelisio.somna.R
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClockWidgetShot {
    @Test
    @Config(sdk = [36], qualifiers = "xxhdpi")
    fun light() = shot("light")

    @Test
    @Config(sdk = [36], qualifiers = "night-xxhdpi")
    fun dark() = shot("dark")

    private fun shot(name: String) {
        val dir = System.getProperty("somna.screenshots.dir")?.let(::File)
        assumeTrue(dir != null)
        dir!!.mkdirs()
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val context = activity
        val view = LayoutInflater.from(context).inflate(R.layout.widget_clock, null)
        val px0 = (190 * context.resources.displayMetrics.density).toInt()
        activity.setContentView(view, android.view.ViewGroup.LayoutParams(px0, px0))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        view.findViewById<TextView>(R.id.clock_time).setTextSize(TypedValue.COMPLEX_UNIT_DIP, (190 - 20 - 34) / 2.1f)
        view.findViewById<TextView>(R.id.clock_alarm).apply { visibility = View.VISIBLE; text = "⏰ demain 06:50" }
        val px = (190 * context.resources.displayMetrics.density).toInt()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        view.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, px, px)
        val bitmap = Bitmap.createBitmap(px + 80, px + 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF7A1F1F.toInt())
        canvas.translate(40f, 40f)
        view.draw(canvas)
        File(dir, "clock-widget-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
