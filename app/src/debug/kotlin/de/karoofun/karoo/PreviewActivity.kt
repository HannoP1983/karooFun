package de.karoofun.karoo

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shows the full page as the Karoo 3 would (478 × 642 px), scaled to the screen width and animated once per second.
 * Extras: "scenario" (COLD, NORMAL, HOT, DESCENT) and "frame" to freeze a single frame for screenshots,
 * "time" (HH:mm today) to see the sky at another time of day, "weather" (CLEAR, PARTLY, OVERCAST, RAIN,
 * STORM, SNOW, FOG) with "wind" in km/h and "wind_from" in degrees (the demo rides east, so 270 is tailwind),
 * or "takeover" to fire a takeover on the ride page. "page" = radar shows the radar tile instead, fetched directly
 * from the internet, at "lat"/"lon" (default Munich) with "bearing"; tapping +/- zooms like on the Karoo.
 */
class PreviewActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug trigger: "takeover" = POKAL, FIRE or PARTY fires it right away on the ride page, ignoring the cooldown
        intent.getStringExtra("takeover")?.let {
            RideStore.fire(ActiveTakeover(Takeover.valueOf(it), 0, System.currentTimeMillis(), 4, 24))
            finish()
            return
        }
        if (intent.getStringExtra("page") == "radar") return showRadar()
        val scenario = DemoRide.Scenario.valueOf(intent.getStringExtra("scenario") ?: "NORMAL")
        val fixed = intent.getIntExtra("frame", -1)
        val time = intent.getStringExtra("time")?.split(":")?.let { (hour, minute) ->
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
            }.timeInMillis
        }
        val units = Units(locale = Locale.GERMANY)
        val renderer = DashboardRenderer(dashboardTexts(), units)
        val rider = Rider(units)
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val screen = resources.displayMetrics.widthPixels
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF333333.toInt()) }
        root.addView(image, FrameLayout.LayoutParams(screen, (screen * KAROO_HEIGHT / KAROO_WIDTH.toFloat()).roundToInt(), Gravity.CENTER))
        setContentView(root)

        var frame = if (fixed >= 0) fixed else 0
        val tick = object : Runnable {
            override fun run() {
                val now = time ?: System.currentTimeMillis()
                val data = DemoRide.snapshot(scenario, frame).let { demo ->
                    intent.getStringExtra("weather")?.let {
                        demo.copy(weather = Weather(Condition.valueOf(it), intent.getIntExtra("wind", 0).toDouble(), intent.getIntExtra("wind_from", 270).toDouble(), now))
                    } ?: demo
                }
                image.setImageBitmap(renderer.render(KAROO_WIDTH, KAROO_HEIGHT, data, rider.zone(data.heartRate, FunSettings.load(this@PreviewActivity)) ?: 1, frame, now))
                if (fixed < 0) {
                    frame++
                    handler.postDelayed(this, 1_000)
                }
            }
        }
        tick.run()
    }

    private fun showRadar() {
        RadarStore.load(this)
        val layout = RadarLayout(KAROO_WIDTH, KAROO_HEIGHT)
        val renderer = RadarRenderer(radarTexts())
        val position = (intent.getStringExtra("lat")?.toDouble() ?: 48.137) to (intent.getStringExtra("lon")?.toDouble() ?: 11.575)
        val rider = RideSnapshot(latitude = position.first, longitude = position.second, bearing = intent.getIntExtra("bearing", 52).toDouble())
        val loader = RadarLoader(layout.mapWidth, layout.mapHeight, null) { url ->
            withContext(Dispatchers.IO) { runCatching { URL(url).readBytes() }.getOrNull() }
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val screen = resources.displayMetrics.widthPixels
        val scale = screen / KAROO_WIDTH.toFloat()
        image.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x / scale
                val y = event.y / scale / KAROO_HEIGHT
                when {
                    y > RadarLayout.BUTTONS_BOTTOM -> RadarStore.step()
                    x < KAROO_WIDTH * 0.75f -> Unit
                    y > RadarLayout.BUTTONS_MIDDLE -> RadarStore.change(this, zoomIn = false)
                    y > RadarLayout.BUTTONS_TOP -> RadarStore.change(this, zoomIn = true)
                }
            }
            true
        }
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF333333.toInt()) }
        root.addView(image, FrameLayout.LayoutParams(screen, (KAROO_HEIGHT * scale).roundToInt(), Gravity.CENTER))
        setContentView(root)
        scope.launch { loader.run({ position }) }
        val fixed = intent.getIntExtra("frame", -1)
        scope.launch {
            var tick = if (fixed >= 0) fixed else 0
            while (isActive) {
                val millis = System.currentTimeMillis()
                val selected = RadarStore.selectedFrame(millis)
                RadarStore.shown = selected ?: RadarTimeline.frameAt(tick)
                image.setImageBitmap(renderer.render(layout, loader.state.value, RadarStore.zoom.value, rider, tick, millis, selected))
                if (fixed < 0) tick++
                delay(1_000)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val KAROO_WIDTH = 478
        const val KAROO_HEIGHT = 642
    }
}
