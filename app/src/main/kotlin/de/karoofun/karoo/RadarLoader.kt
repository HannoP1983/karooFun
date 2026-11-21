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

/** A radar frame: the cells as delivered (four pixels per kilometer) for asking about rain, and the smoothed picture for the tile. */
class RadarFrame(val cells: RadarCells, val bitmap: Bitmap, val fetchedFor: Long) {
    /** Rain at a point given as fraction of the map width and height; outside the map or without data unknown. */
    fun rainAt(u: Float, v: Float): Boolean? = cells.rainAt(u, v)

    companion object {
        /** Decodes a frame and smooths it for a map [mapWidth] pixels wide, null if the bytes are no image. */
        fun decode(bytes: ByteArray, mapWidth: Int, fetchedFor: Long): RadarFrame? {
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val width = raw.width
            val height = raw.height
            val pixels = IntArray(width * height)
            raw.getPixels(pixels, 0, width, 0, 0, width, height)
            raw.recycle()
            val factor = RadarImage.factorFor(width, mapWidth)
            val smooth = RadarImage.smooth(pixels, width, height, factor)
            val bitmap = Bitmap.createBitmap(smooth, width * factor, height * factor, Bitmap.Config.ARGB_8888)
            return RadarFrame(RadarCells.of(pixels, width, height), bitmap, fetchedFor)
        }
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
 * Process-wide radar: zoom level, tile size and the loader. Once the radar tile or the rain field was shown,
 * loading keeps running in the background for the whole ride (and after restarts), so both are ready when
 * swiped to. The base map is only loaded once the tile was shown, the rain field needs just the radar.
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
        start(context, scope, prefs.getInt(KEY_WIDTH, DEFAULT_WIDTH), prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT), prefs.contains(KEY_WIDTH), get, position)
    }

    /** Called by the rain field: keeps loading running, with the radar tile's map size if it was ever shown. */
    @Synchronized
    fun ensure(context: Context, scope: CoroutineScope, get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?) {
        if (loader != null && job?.isActive == true) return
        val prefs = prefs(context)
        prefs.edit().putBoolean(KEY_USED, true).apply()
        start(context, scope, prefs.getInt(KEY_WIDTH, DEFAULT_WIDTH), prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT), prefs.contains(KEY_WIDTH), get, position)
    }

    /** Called by the tile: remembers its map size and makes sure loading runs for it. */
    @Synchronized
    fun use(context: Context, scope: CoroutineScope, mapWidth: Int, mapHeight: Int, get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?) {
        prefs(context).edit().putBoolean(KEY_USED, true).putInt(KEY_WIDTH, mapWidth).putInt(KEY_HEIGHT, mapHeight).apply()
        val current = loader
        if (current != null && current.mapWidth == mapWidth && current.mapHeight == mapHeight && job?.isActive == true) {
            current.withMap = true
            return
        }
        start(context, scope, mapWidth, mapHeight, true, get, position)
    }

    @Synchronized
    private fun start(
        context: Context, scope: CoroutineScope, width: Int, height: Int, withMap: Boolean,
        get: suspend (String) -> ByteArray?, position: () -> Pair<Double, Double>?,
    ) {
        load(context)
        job?.cancel()
        val next = RadarLoader(width, height, File(context.cacheDir, "radar-maps"), withMap, get)
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
 * Loads base map and radar frames. All frames are fetched again every [RadarTimeline.RELOAD_MS], since each
 * DWD run brings a new forecast. Requests run one after another, "now" first, since they share the slow
 * Bluetooth link to the phone. The first round shows each frame as it arrives; later rounds swap all frames
 * at once, so tile and field never mix two forecasts. A failed frame is skipped and fetched again a minute
 * later, the old one standing in meanwhile. [withMap] is set once the tile is shown. Base maps are kept
 * on disk, so places ridden before show up at once.
 */
class RadarLoader(
    val mapWidth: Int,
    val mapHeight: Int,
    private val cache: File?,
    @Volatile var withMap: Boolean = true,
    private val get: suspend (String) -> ByteArray?,
) {
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
                // A new window doesn't wait for the retry: after a fix following a stale position it is needed right away
                millis >= retryAt || _state.value.window?.let { wrong(it, here) } == true -> refresh(here.first, here.second, millis, position)
            }
            delay(TICK_MS)
        }
    }

    /** [window] no longer fits: zoom changed, or the rider got away from it. */
    private fun wrong(window: RadarWindow, here: Pair<Double, Double>?) =
        window.zoom != RadarStore.zoom.value || here != null && window.needsRecenter(here.first, here.second)

    /**
     * One pass of loading. It stops as soon as the window it loads for no longer fits, for example when the
     * first real GPS fix arrives while a round for the Karoo's stale position is still running.
     */
    private suspend fun refresh(lat: Double, lon: Double, millis: Long, position: () -> Pair<Double, Double>?) {
        val zoom = RadarStore.zoom.value
        val current = _state.value
        val window = current.window
        if (window == null || window.zoom != zoom || window.needsRecenter(lat, lon)) {
            val next = RadarWindow.around(lat, lon, zoom, mapWidth, mapHeight)
            _state.value = RadarState(window = next, previous = current.takeIf { it.base != null }?.copy(previous = null))
        }
        var failed = false
        val mapWindow = _state.value.window ?: return
        if (withMap && _state.value.base == null) {
            val base = loadBase(mapWindow)
            if (_state.value.window != mapWindow || wrong(mapWindow, position())) return
            if (base != null) _state.update { it.copy(base = base) } else failed = true
        }

        val state = _state.value
        val latest = RadarTimeline.now(millis)
        val now = if (state.now == 0L || latest - state.now >= RadarTimeline.RELOAD_MS) latest else state.now
        val missing = RadarTimeline.OFFSETS_MIN.indices.filter { state.frames[RadarTimeline.time(now, it)]?.fetchedFor != now }
        val firstRound = state.frames.isEmpty()
        val loaded = mutableMapOf<Long, RadarFrame>()
        var failures = 0
        for (index in missing) {
            val window = _state.value.window ?: return
            val time = RadarTimeline.time(now, index)
            val frame = get(window.radarUrl(time))?.let { RadarFrame.decode(it, mapWidth, now) }
            if (_state.value.window != window || wrong(window, position())) return
            if (frame == null) {
                failed = true
                // Two misses in a row: the connection is likely gone, so don't wait out every request's timeout
                if (++failures >= MAX_FAILURES) break
                continue
            }
            failures = 0
            loaded[time] = frame
            if (firstRound) publish(loaded, now, millis)
        }
        publish(loaded, now, millis)
        if (failed) retryLater(millis, nothingShown = _state.value.frames.isEmpty())
    }

    /** Adds [loaded] frames, drops those older than [now], and clears [loaded]. */
    private fun publish(loaded: MutableMap<Long, RadarFrame>, now: Long, millis: Long) {
        if (loaded.isEmpty()) return
        val frames = loaded.toMap()
        loaded.clear()
        _state.update { s -> s.copy(frames = s.frames.filterKeys { it >= now } + frames, now = now, loadedAt = millis, previous = null) }
    }

    private suspend fun loadBase(window: RadarWindow): Bitmap? {
        val file = cache?.let { File(it, window.cacheKey + ".png") }
        file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.let {
            file.setLastModified(System.currentTimeMillis())
            return it
        }
        val halves = (0..1).map { half -> get(window.baseMapUrl(half))?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: return null }
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

    /** Soon while nothing is shown yet (the phone link often comes up only after start), else in a minute. */
    private fun retryLater(millis: Long, nothingShown: Boolean) {
        retryAt = millis + if (nothingShown) FIRST_RETRY_MS else RETRY_MS
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val RETRY_MS = 60_000L
        const val FIRST_RETRY_MS = 15_000L
        const val MAX_FAILURES = 2
        const val MAX_CACHED_MAPS = 60
    }
}
