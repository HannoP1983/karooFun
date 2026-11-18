package de.karoofun.karoo

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
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
import java.io.File
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
 * "lang" (e.g. de) renders the texts in that language, "zoom" (CITY, REGION, OVERVIEW) sets the radar zoom.
 * "page" = fields shows single comic fields on a 2 × 4 page, "fields" picks them (comma-separated ComicField names, default the first eight; "ELEVATION:2" spans both columns).
 * "save" writes the page as <save>.png to the app's external files dir once "save_tick" is reached, for the promo page.
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
        if (intent.getStringExtra("page") == "fields") return showFields()
        val scenario = DemoRide.Scenario.valueOf(intent.getStringExtra("scenario") ?: "NORMAL")
        val fixed = intent.getIntExtra("frame", -1)
        val time = intent.getStringExtra("time")?.split(":")?.let { (hour, minute) ->
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
            }.timeInMillis
        }
        val units = Units(locale = numberLocale())
        val renderer = DashboardRenderer(texts().dashboardTexts(), units)
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
                val bitmap = renderer.render(KAROO_WIDTH, KAROO_HEIGHT, data, rider.zone(data.heartRate, FunSettings.load(this@PreviewActivity)) ?: 1, frame, now)
                image.setImageBitmap(bitmap)
                saveIfRequested(bitmap, frame)
                if (fixed < 0) {
                    frame++
                    handler.postDelayed(this, 1_000)
                }
            }
        }
        tick.run()
    }

    private fun showFields() {
        val scenario = DemoRide.Scenario.valueOf(intent.getStringExtra("scenario") ?: "NORMAL")
        val fixed = intent.getIntExtra("frame", -1)
        val fields = intent.getStringExtra("fields")?.split(",")?.map { entry ->
            val (name, span) = (entry.trim().split(":") + "1").let { it[0] to it[1].toInt() }
            ComicField.valueOf(name) to span
        } ?: ComicField.entries.take(8).map { it to 1 }
        val context = texts()
        val renderer = FieldRenderer(context.fieldLabels(), context.dashboardTexts(), Units(locale = numberLocale()))
        val rider = Rider(Units(locale = numberLocale()))
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val screen = resources.displayMetrics.widthPixels
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF333333.toInt()) }
        root.addView(image, FrameLayout.LayoutParams(screen, (screen * KAROO_HEIGHT / KAROO_WIDTH.toFloat()).roundToInt(), Gravity.CENTER))
        setContentView(root)

        val cellWidth = KAROO_WIDTH / 2
        val cellHeight = KAROO_HEIGHT / 4
        var frame = if (fixed >= 0) fixed else 0
        val tick = object : Runnable {
            override fun run() {
                val data = DemoRide.snapshot(scenario, frame)
                val settings = FunSettings.load(this@PreviewActivity)
                val page = Bitmap.createBitmap(KAROO_WIDTH, KAROO_HEIGHT, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(page)
                canvas.drawColor(0xFF000000.toInt())
                var slot = 0
                for ((field, span) in fields) {
                    val cell = renderer.render(cellWidth * span, cellHeight, field, data, rider, settings, frame)
                    canvas.drawBitmap(cell, (slot % 2 * cellWidth).toFloat(), (slot / 2 * cellHeight).toFloat(), null)
                    slot += span
                }
                image.setImageBitmap(page)
                saveIfRequested(page, frame)
                if (fixed < 0) {
                    frame++
                    handler.postDelayed(this, 1_000)
                }
            }
        }
        tick.run()
    }

    private fun showRadar() {
        intent.getStringExtra("zoom")?.let { getSharedPreferences("radar", MODE_PRIVATE).edit().putString("zoom", it).commit() }
        RadarStore.load(this)
        val layout = RadarLayout(KAROO_WIDTH, KAROO_HEIGHT)
        val renderer = RadarRenderer(texts().radarTexts())
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
                val bitmap = renderer.render(layout, loader.state.value, RadarStore.zoom.value, rider, tick, millis, selected)
                image.setImageBitmap(bitmap)
                saveIfRequested(bitmap, tick)
                if (fixed < 0) tick++
                delay(1_000)
            }
        }
    }

    /** Decimal separator to match "lang": German by default, like on a German Karoo. */
    private fun numberLocale(): Locale = intent.getStringExtra("lang")?.let { Locale(it) } ?: Locale.GERMANY

    private fun texts(): Context {
        val lang = intent.getStringExtra("lang") ?: return this
        return createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale(lang)) })
    }

    private fun saveIfRequested(bitmap: Bitmap, tick: Int) {
        val name = intent.getStringExtra("save") ?: return
        if (tick != intent.getIntExtra("save_tick", 0)) return
        File(getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
