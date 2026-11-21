package de.karoofun.karoo

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Comic-styled settings for the takeovers, with a preview button per animation. */
class SettingsActivity : Activity() {
    private lateinit var settings: FunSettings
    private val hand: Typeface = Typeface.create("casual", Typeface.BOLD)
    private val numbers: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val handler = Handler(Looper.getMainLooper())
    private var preview: FrameLayout? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.localized())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = FunSettings.load(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAPER)
            setPadding(0, 0, 0, dp(24))
        }
        column.addView(header())
        column.addView(card(getString(R.string.settings_language), getString(R.string.settings_language_hint), null, null, languages()))
        column.addView(card(getString(R.string.settings_zones), getString(R.string.settings_zones_hint),
            Toggle({ it.ownZones }) { s, v -> s.copy(ownZones = v) }, null,
            stepper(getString(R.string.settings_max_hr), "bpm", { it.maxHr }, FunSettings.MAX_HR_RANGE) { s, v -> s.withMaxHr(v) },
            stepper(getString(R.string.settings_zone_from, 2), "bpm", { it.zone2From }, FunSettings.ZONE_START_RANGE) { s, v ->
                s.copy(zone2From = v.coerceAtMost(s.zone3From - 1)) },
            stepper(getString(R.string.settings_zone_from, 3), "bpm", { it.zone3From }, FunSettings.ZONE_START_RANGE) { s, v ->
                s.copy(zone3From = v.coerceIn(s.zone2From + 1, s.zone4From - 1)) },
            stepper(getString(R.string.settings_zone_from, 4), "bpm", { it.zone4From }, FunSettings.ZONE_START_RANGE) { s, v ->
                s.copy(zone4From = v.coerceIn(s.zone3From + 1, s.zone5From - 1)) },
            stepper(getString(R.string.settings_zone_from, 5), "bpm", { it.zone5From }, FunSettings.ZONE_START_RANGE) { s, v ->
                s.copy(zone5From = v.coerceAtLeast(s.zone4From + 1)) },
        ))
        column.addView(card(getString(R.string.settings_gears), getString(R.string.settings_gears_hint),
            Toggle({ it.ownGears }) { s, v -> s.copy(ownGears = v) }, null,
            stepper(getString(R.string.settings_front_gears), "", { it.frontGears }, FunSettings.FRONT_GEARS_RANGE) { s, v -> s.copy(frontGears = v) },
            stepper(getString(R.string.settings_rear_gears), "", { it.rearGears }, FunSettings.REAR_GEARS_RANGE) { s, v -> s.copy(rearGears = v) },
        ))
        column.addView(card(getString(R.string.settings_all), getString(R.string.settings_all_hint), null, null,
            stepper(getString(R.string.settings_cooldown), "min", { it.cooldownMinutes }, FunSettings.COOLDOWN_RANGE, 5) { s, v -> s.copy(cooldownMinutes = v) },
            stepper(getString(R.string.settings_duration), "s", { it.durationSeconds }, FunSettings.DURATION_RANGE) { s, v -> s.copy(durationSeconds = v) },
        ))
        column.addView(card(getString(R.string.settings_pokal), getString(R.string.settings_pokal_hint),
            Toggle({ it.pokalEnabled }) { s, v -> s.copy(pokalEnabled = v) }, Takeover.POKAL,
            stepper(getString(R.string.settings_pokal_speed), "km/h", { it.pokalSpeedKmh }, FunSettings.POKAL_SPEED_RANGE, 5) { s, v -> s.copy(pokalSpeedKmh = v) },
            stepper(getString(R.string.settings_pokal_grade), "%", { it.pokalGrade }, FunSettings.POKAL_GRADE_RANGE) { s, v -> s.copy(pokalGrade = v) },
        ))
        column.addView(card(getString(R.string.settings_summit), getString(R.string.settings_summit_hint),
            Toggle({ it.summitEnabled }) { s, v -> s.copy(summitEnabled = v) }, Takeover.SUMMIT,
            stepper(getString(R.string.settings_summit_min), "hm", { it.summitMinMeters }, FunSettings.SUMMIT_MIN_RANGE, 10) { s, v -> s.copy(summitMinMeters = v) },
        ))
        column.addView(card(getString(R.string.settings_kilowatt), getString(R.string.settings_kilowatt_hint),
            Toggle({ it.kilowattEnabled }) { s, v -> s.copy(kilowattEnabled = v) }, Takeover.KILOWATT,
            stepper(getString(R.string.settings_kilowatt_watts), "W", { it.kilowattWatts }, FunSettings.KILOWATT_RANGE, 50) { s, v -> s.copy(kilowattWatts = v) },
        ))
        column.addView(card(getString(R.string.settings_fire), getString(R.string.settings_fire_hint),
            Toggle({ it.fireEnabled }) { s, v -> s.copy(fireEnabled = v) }, Takeover.FIRE,
            stepper(getString(R.string.settings_fire_above), "%", { it.fireAbovePercent }, FunSettings.FIRE_ABOVE_RANGE, 5, prefix = "+") { s, v -> s.copy(fireAbovePercent = v) },
            stepper(getString(R.string.settings_fire_zone), "", { it.fireMinZone }, FunSettings.ZONE_RANGE, prefix = "Z") { s, v -> s.copy(fireMinZone = v) },
            stepper(getString(R.string.settings_fire_seconds), "s", { it.fireSeconds }, FunSettings.FIRE_SECONDS_RANGE, 5) { s, v -> s.copy(fireSeconds = v) },
        ))
        column.addView(card(getString(R.string.settings_boil), getString(R.string.settings_boil_hint),
            Toggle({ it.boilEnabled }) { s, v -> s.copy(boilEnabled = v) }, Takeover.BOILING,
            stepper(getString(R.string.settings_boil_percent), "% FTP", { it.boilPercent }, FunSettings.BOIL_PERCENT_RANGE, 5) { s, v -> s.copy(boilPercent = v) },
            stepper(getString(R.string.settings_boil_minutes), "min", { it.boilMinutes }, FunSettings.BOIL_MINUTES_RANGE) { s, v -> s.copy(boilMinutes = v) },
        ))
        column.addView(card(getString(R.string.settings_party), getString(R.string.settings_party_hint),
            Toggle({ it.partyEnabled }) { s, v -> s.copy(partyEnabled = v) }, Takeover.PARTY,
            stepper(getString(R.string.settings_party_from), "", { it.partyMinZone }, FunSettings.ZONE_RANGE, prefix = "Z") { s, v -> s.copy(partyMinZone = v, partyMaxZone = maxOf(v, s.partyMaxZone)) },
            stepper(getString(R.string.settings_party_to), "", { it.partyMaxZone }, FunSettings.ZONE_RANGE, prefix = "Z") { s, v -> s.copy(partyMaxZone = v, partyMinZone = minOf(v, s.partyMinZone)) },
            stepper(getString(R.string.settings_party_minutes), "min", { it.partyMinutes }, FunSettings.PARTY_MINUTES_RANGE, 5) { s, v -> s.copy(partyMinutes = v) },
            stepper(getString(R.string.settings_party_tolerance), "%", { it.partyTolerancePercent }, FunSettings.PARTY_TOLERANCE_RANGE) { s, v -> s.copy(partyTolerancePercent = v) },
        ))
        // Open-Meteo's license (CC BY 4.0) asks for a visible credit
        column.addView(label(getString(R.string.settings_weather_credit), 13f, bold = false).apply {
            setPadding(dp(20), dp(8), dp(20), dp(20))
        })
        val root = FrameLayout(this)
        root.addView(ScrollView(this).apply { addView(column, MATCH_PARENT, WRAP_CONTENT) })
        setContentView(root)
        if (intent.hasExtra(EXTRA_PREVIEW)) {
            Takeover.entries.getOrNull(intent.getIntExtra(EXTRA_PREVIEW, 0))?.let { play(it, intent.getIntExtra(EXTRA_FRAME, -1)) }
        }
    }

    private fun update(change: (FunSettings) -> FunSettings) {
        settings = change(settings)
        settings.save(this)
        refreshers.forEach { it() }
    }

    private val refreshers = ArrayList<() -> Unit>()

    private class Toggle(val get: (FunSettings) -> Boolean, val set: (FunSettings, Boolean) -> FunSettings)

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(18))
        background = LayerDrawable(arrayOf(GradientDrawable().apply { setColor(INK) }, GradientDrawable().apply { setColor(0xFFA884F3.toInt()) }))
            .apply { setLayerInset(1, 0, 0, 0, dp(3)) }
        val title = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(TextView(context).apply {
            text = getString(R.string.app_name)
            typeface = numbers
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * textScale)
            setTextColor(Color.WHITE)
            setShadowLayer(1f, dp(3).toFloat(), dp(3).toFloat(), INK)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        // Version as a small sticker, so it's easy to tell which build is on the Karoo
        title.addView(label(getString(R.string.settings_version, versionName()), 14f).apply {
            background = sticker(Color.WHITE, 12, 3)
            setPadding(dp(10), dp(3), dp(12), dp(5))
        })
        addView(title)
        addView(label(getString(R.string.settings_subtitle), 15f))
    }

    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

    private fun card(title: String, hint: String, toggle: Toggle?, takeover: Takeover?, vararg rows: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(20), dp(20))
        background = sticker(Color.WHITE, 18)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(16), dp(18), dp(16), 0) }
        val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label(title, 20f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (toggle != null) titleRow.addView(toggleView(toggle))
        addView(titleRow)
        addView(label(hint, 14f, bold = false).apply { setPadding(0, dp(6), 0, 0) })
        rows.forEach { addView(it) }
        if (takeover != null) addView(button(getString(R.string.settings_preview)) { play(takeover) })
    }

    private fun stepper(
        title: String,
        unit: String,
        get: (FunSettings) -> Int,
        range: IntRange,
        step: Int = 1,
        prefix: String = "",
        set: (FunSettings, Int) -> FunSettings,
    ) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, 0)
        addView(label(title, 16f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val value = TextView(context).apply {
            typeface = numbers
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 23f * textScale)
            setTextColor(INK)
            gravity = Gravity.CENTER
        }
        fun refresh() {
            val v = get(settings)
            value.text = if (prefix == "+" && v < 0) "$v $unit".trim() else "$prefix$v $unit".trim()
        }
        refreshers += ::refresh
        refresh()
        addView(round("–") { update { s -> set(s, (get(s) - step).coerceIn(range)) } })
        addView(value, LinearLayout.LayoutParams(dp(140), WRAP_CONTENT))
        addView(round("+") { update { s -> set(s, (get(s) + step).coerceIn(range)) } })
    }

    private fun toggleView(toggle: Toggle) = TextView(this).apply {
        typeface = numbers
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f * textScale)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(6), dp(14), dp(6))
        fun refresh() {
            val on = toggle.get(settings)
            text = getString(if (on) R.string.toggle_on else R.string.toggle_off)
            setTextColor(if (on) Color.WHITE else INK)
            background = sticker(if (on) 0xFF3FBF66.toInt() else 0xFFE4E0D6.toInt(), 18, shadow = 2)
        }
        refreshers += ::refresh
        refresh()
        setOnClickListener { update { s -> toggle.set(s, !toggle.get(s)) } }
    }

    /** One chip per language; switching rebuilds the screen in the new language. */
    private fun languages() = LinearLayout(this).apply {
        setPadding(0, dp(14), 0, 0)
        val names = mapOf(FunSettings.LANGUAGE_AUTO to getString(R.string.settings_language_auto), "de" to "Deutsch", "en" to "English")
        for ((code, name) in names) {
            val on = settings.language == code
            addView(TextView(context).apply {
                text = name
                typeface = numbers
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * textScale)
                setTextColor(if (on) Color.WHITE else INK)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = sticker(if (on) 0xFF3FBF66.toInt() else 0xFFE4E0D6.toInt(), 18, shadow = 2)
                setOnClickListener {
                    if (on) return@setOnClickListener
                    update { s -> s.copy(language = code) }
                    recreate()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(8), 0) })
        }
    }

    private fun round(symbol: String, onClick: () -> Unit) = TextView(this).apply {
        text = symbol
        typeface = numbers
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f * textScale)
        setTextColor(INK)
        gravity = Gravity.CENTER
        background = sticker(0xFFFFD23F.toInt(), 24, shadow = 3)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        // Holding the button keeps counting, so heart rates do not need dozens of taps
        val repeat = object : Runnable {
            override fun run() {
                onClick()
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onClick()
                    handler.postDelayed(repeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(repeat)
            }
            true
        }
    }

    private fun button(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        typeface = numbers
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * textScale)
        setTextColor(Color.WHITE)
        setShadowLayer(1f, dp(2).toFloat(), dp(2).toFloat(), INK)
        gravity = Gravity.CENTER
        background = sticker(0xFFFF5A3C.toInt(), 14, shadow = 3)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply { topMargin = dp(16) }
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float, bold: Boolean = true) = TextView(this).apply {
        this.text = text
        typeface = if (bold) hand else Typeface.create("casual", Typeface.NORMAL)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * textScale)
        setTextColor(INK)
    }

    /** White comic sticker: ink outline with a hard shadow to the bottom right. */
    private fun sticker(color: Int, radius: Int, shadow: Int = 4): LayerDrawable {
        val r = dp(radius).toFloat()
        val layers = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(INK); cornerRadius = r },
            GradientDrawable().apply { setColor(color); cornerRadius = r; setStroke(dp(3), INK) },
        ))
        layers.setLayerInset(0, dp(shadow), dp(shadow), 0, 0)
        layers.setLayerInset(1, 0, 0, dp(shadow), dp(shadow))
        return layers
    }

    /**
     * Plays a takeover full screen with demo values, the way it appears on the ride page.
     * [fixedFrame] ≥ 0 shows a single frame instead (for screenshots).
     */
    private fun play(type: Takeover, fixedFrame: Int = -1) {
        val root = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        preview?.let { root.removeView(it) }
        val width = 480
        val height = 642
        val count = settings.durationSeconds * TakeoverArt.FPS
        val overlay = FrameLayout(this).apply { setBackgroundColor(0xEE1F2328.toInt()); isClickable = true }
        val box = FrameLayout(this)
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val panel = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_END }
        box.addView(image, MATCH_PARENT, MATCH_PARENT)
        box.addView(panel, MATCH_PARENT, MATCH_PARENT)
        val screen = resources.displayMetrics.widthPixels
        overlay.addView(box, FrameLayout.LayoutParams(screen, (screen * height / width.toFloat()).roundToInt(), Gravity.CENTER))
        root.addView(overlay, MATCH_PARENT, MATCH_PARENT)
        preview = overlay
        overlay.setOnClickListener { root.removeView(overlay); preview = null }

        thread {
            val art = TakeoverArt(takeoverTexts())
            val scenario = when (type) {
                Takeover.POKAL -> DemoRide.Scenario.DESCENT
                Takeover.FIRE, Takeover.KILOWATT, Takeover.BOILING -> DemoRide.Scenario.HOT
                else -> DemoRide.Scenario.NORMAL
            }
            val data = DemoRide.snapshot(scenario, 0).let {
                when (type) {
                    Takeover.PARTY -> it.copy(heartRate = 138.0, speed = 25.4 / 3.6, averageSpeed = 25.1 / 3.6)
                    Takeover.KILOWATT -> it.copy(power = 1240.0)
                    Takeover.SUMMIT -> it.copy(summitHeight = 384.0)
                    else -> it
                }
            }
            val zone = Rider().zone(data.heartRate, settings)
            val takeover = ActiveTakeover(type, 0, 0, zone, settings.partyMinutes + 4, settings.boilMinutes * 60 + 14)
            val panelBitmap = art.renderPanel(type, width, panelInfo(takeover, data, settings, Units()))
            val frames: List<Bitmap> = if (fixedFrame >= 0) listOf(art.renderFrame(type, width, height, fixedFrame, count, 0))
            else (0 until count).map { art.renderFrame(type, width, height, it, count, 0) }
            handler.post {
                panel.setImageBitmap(panelBitmap)
                var i = 0
                val tick = object : Runnable {
                    override fun run() {
                        if (preview != overlay) return
                        image.setImageBitmap(frames[i % frames.size])
                        i++
                        // Loop with a short pause, so the takeover can be watched several times
                        handler.postDelayed(this, if (i % frames.size == 0) 1_000L else 1_000L / TakeoverArt.FPS)
                    }
                }
                tick.run()
            }
        }
    }

    /** Sizes are designed for the Karoo's 480 px wide screen and scaled to the actual width. */
    private val scale get() = resources.displayMetrics.widthPixels / 480f

    /** Text a bit larger than the layout, for reading at arm's length on the bike. */
    private val textScale get() = scale * TEXT_ZOOM

    private fun dp(value: Int) = (value * scale).roundToInt()

    companion object {
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_FRAME = "frame"
        private const val REPEAT_DELAY_MS = 400L
        private const val REPEAT_MS = 70L
        private const val TEXT_ZOOM = 1.2f
    }
}
