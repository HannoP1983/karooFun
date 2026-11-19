package de.karoofun.karoo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

/** A radar frame as delivered: about four pixels per kilometer, drawn soft and blob-like when scaled up. */
class RadarFrame(val bitmap: Bitmap, val fetchedFor: Long) {
    /** Rain at a point given as fraction of the map width and height; outside the map counts as unknown. */
    fun rainAt(u: Float, v: Float): Boolean? {
        val x = (u * bitmap.width).toInt()
        val y = (v * bitmap.height).toInt()
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return null
        return (bitmap.getPixel(x, y) ushr 24) > RAIN_ALPHA
    }

    private companion object {
        const val RAIN_ALPHA = 64
    }
}

/**
 * Everything the radar tile draws. [previous] keeps the last map while a new one loads after zooming
 * or riding off the map, so the tile never goes blank.
 */
data class RadarState(
    val window: RadarWindow? = null,
    val base: Bitmap? = null,
    val frames: Map<Long, RadarFrame> = emptyMap(),
    val now: Long = 0,
    val loadedAt: Long = 0,
    val outside: Boolean = false,
    val previous: RadarState? = null,
)

/**
 * Process-wide radar: zoom level, tile size and the loader. Once the radar tile was shown, loading keeps
 * running in the background for the whole ride (and after restarts), so the tile is ready when swiped to.
 */
object RadarStore {
    private val _zoom = MutableStateFlow(RadarZoom.REGION)
    val zoom: StateFlow<RadarZoom> = _zoom.asStateFlow()

    /** Timeline frame picked by tapping, with the time of the tap; it holds for [HOLD_MS], then the animation resumes. */
    @Volatile
    private var selection: Pair<Int, Long>? = null

    /** Frame the tile showed last, so the first tap continues from what the rider sees. */
    @Volatile
    var shown = RadarTimeline.NOW_INDEX

    private var loader: RadarLoader? = null
    private var job: Job? = null
    val state: StateFlow<RadarState>? get() = loader?.state

    fun load(context: Context) {
        val saved = prefs(context).getString(KEY_ZOOM, null)
        _zoom.value = RadarZoom.entries.firstOrNull { it.name == saved } ?: RadarZoom.REGION
    }

    fun change(context: Context, zoomIn: Boolean) {
        _zoom.update { if (zoomIn) it.zoomIn() else it.zoomOut() }
        prefs(context).edit().putString(KEY_ZOOM, _zoom.value.name).apply()
    }

    /** One tap on the timeline: one frame further, skipping frames still loading, after the last one back to "now". */
    fun step(millis: Long = System.currentTimeMillis()) {
        val current = state?.value
        var index = selectedFrame(millis) ?: shown
        repeat(RadarTimeline.OFFSETS_MIN.size) {
            index = RadarTimeline.next(index)
            if (current == null || index == RadarTimeline.NOW_INDEX || current.frames.containsKey(RadarTimeline.time(current.now, index))) {
                selection = index to millis
                return
            }
        }
    }

    fun selectedFrame(millis: Long): Int? = selection?.takeIf { millis - it.second < HOLD_MS }?.first

    /** Starts background loading if the radar tile was ever used, with the map size it had. */
    fun startIfUsed(context: Context, scope: CoroutineScope, get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_USED, false)) return
        start(context, scope, prefs.getInt(KEY_WIDTH, DEFAULT_WIDTH), prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT), get, position)
    }

    /** Called by the rain field: keeps loading running, with the radar tile's map size if it was ever shown. */
    @Synchronized
    fun ensure(context: Context, scope: CoroutineScope, get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?) {
        if (loader != null && job?.isActive == true) return
        val prefs = prefs(context)
        prefs.edit().putBoolean(KEY_USED, true).apply()
        start(context, scope, prefs.getInt(KEY_WIDTH, DEFAULT_WIDTH), prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT), get, position)
    }

    /** Called by the tile: remembers its map size and makes sure loading runs for it. */
    @Synchronized
    fun use(context: Context, scope: CoroutineScope, mapWidth: Int, mapHeight: Int, get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?) {
        prefs(context).edit().putBoolean(KEY_USED, true).putInt(KEY_WIDTH, mapWidth).putInt(KEY_HEIGHT, mapHeight).apply()
        val current = loader
        if (current != null && current.mapWidth == mapWidth && current.mapHeight == mapHeight && job?.isActive == true) return
        start(context, scope, mapWidth, mapHeight, get, position)
    }

    @Synchronized
    private fun start(context: Context, scope: CoroutineScope, width: Int, height: Int, get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?) {
        load(context)
        job?.cancel()
        val next = RadarLoader(width, height, File(context.cacheDir, "radar-maps"), get)
        loader = next
        job = scope.launch { next.run(position) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("radar", Context.MODE_PRIVATE)
    private const val KEY_ZOOM = "zoom"
    private const val KEY_USED = "used"
    private const val KEY_WIDTH = "map_width"
    private const val KEY_HEIGHT = "map_height"
    private const val DEFAULT_WIDTH = 478
    private const val DEFAULT_HEIGHT = 494
    private const val HOLD_MS = 20_000L
}

/**
 * Loads base map and radar frames. Forecast frames are fetched again with every new 5 minute slot. Requests
 * run one after another, "now" first, since they share the slow Bluetooth link to the phone. Base maps are kept
 * on disk, so places ridden before show up at once.
 */
class RadarLoader(val mapWidth: Int, val mapHeight: Int, private val cache: File?, private val get: suspend (String) -> ByteArray?) {
    private val _state = MutableStateFlow(RadarState())
    val state: StateFlow<RadarState> = _state.asStateFlow()

    private var retryAt = 0L

    suspend fun run(position: () -> Pair<Double, Double>?, clock: () -> Long = System::currentTimeMillis) {
        while (coroutineContext.isActive) {
            val here = position()
            val millis = clock()
            when {
                here == null -> Unit
                !RadarWindow.covers(here.first, here.second) -> _state.value = RadarState(outside = true)
                millis >= retryAt || zoomChanged() -> refresh(here.first, here.second, millis)
            }
            delay(TICK_MS)
        }
    }

    private fun zoomChanged() = _state.value.window?.zoom?.let { it != RadarStore.zoom.value } ?: false

    private suspend fun refresh(lat: Double, lon: Double, millis: Long) {
        val zoom = RadarStore.zoom.value
        val current = _state.value
        val window = current.window
        if (window == null || window.zoom != zoom || window.needsRecenter(lat, lon)) {
            val next = RadarWindow.around(lat, lon, zoom, mapWidth, mapHeight)
            _state.value = RadarState(window = next, previous = current.takeIf { it.base != null }?.copy(previous = null))
            val base = loadBase(next) ?: return retryLater(millis)
            if (_state.value.window != next) return
            _state.update { it.copy(base = base) }
        }
        val now = RadarTimeline.now(millis)
        for (index in RadarTimeline.OFFSETS_MIN.indices) {
            val state = _state.value
            val time = RadarTimeline.time(now, index)
            val frame = state.frames[time]
            if (frame != null && (time <= now || frame.fetchedFor == now)) continue
            val window = state.window ?: return
            val bitmap = get(window.radarUrl(time))?.let(::decode) ?: return retryLater(millis)
            if (_state.value.window != window) return
            _state.update { s ->
                val kept = s.frames.filterKeys { it >= RadarTimeline.time(now, 0) }
                s.copy(frames = kept + (time to RadarFrame(bitmap, now)), now = now, loadedAt = millis, previous = null)
            }
        }
        _state.update { it.copy(now = now) }
    }

    private suspend fun loadBase(window: RadarWindow): Bitmap? {
        val file = cache?.let { File(it, window.cacheKey + ".png") }
        file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.let {
            file.setLastModified(System.currentTimeMillis())
            return it
        }
        val halves = (0..1).map { half -> get(window.baseMapUrl(half))?.let(::decode) ?: return null }
        val base = Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.RGB_565).also {
            val c = Canvas(it)
            c.drawBitmap(halves[0], 0f, 0f, null)
            c.drawBitmap(halves[1], 0f, (mapHeight / 2).toFloat(), null)
        }
        if (file != null) runCatching { store(file, base) }
        return base
    }

    /** Keeps the newest base maps on disk, about 150 KB each. */
    private fun store(file: File, base: Bitmap) {
        file.parentFile?.mkdirs()
        file.outputStream().use { base.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.parentFile?.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_CACHED_MAPS)?.forEach { it.delete() }
    }

    private fun retryLater(millis: Long) {
        retryAt = millis + RETRY_MS
    }

    private fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private companion object {
        const val TICK_MS = 1_000L
        const val RETRY_MS = 60_000L
        const val MAX_CACHED_MAPS = 60
    }
}
