package de.karoofun.karoo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * DWD rain radar on a grey map as its own tile, zoomable with the +/- buttons on the tile. Loading is done by
 * [RadarStore], which keeps going in the background so the tile is ready whenever it is swiped to.
 */
class RadarDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            RadarStore.zoom.collect {
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to it.widthKm))))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        RadarStore.load(context)
        val (width, height) = config.viewSize
        val layout = RadarLayout(width, height)
        val renderer = RadarRenderer(context.localized().radarTexts())
        if (!config.preview) RadarStore.use(context, background, layout.mapWidth, layout.mapHeight, { karooSystem.httpGet(it) }, ::riderPosition)
        val job = CoroutineScope(Dispatchers.IO).launch {
            var tick = 0
            while (isActive) {
                val rider = if (config.preview) DemoRide.snapshot(DemoRide.Scenario.NORMAL, tick) else RideStore.state.value
                val views = RemoteViews(context.packageName, R.layout.radar_view)
                val millis = System.currentTimeMillis()
                val state = RadarStore.state?.value ?: RadarState()
                val selected = RadarStore.selectedFrame(millis)
                RadarStore.shown = selected ?: RadarTimeline.frameAt(tick)
                views.setImageViewBitmap(R.id.radar_image, renderer.render(layout, state, RadarStore.zoom.value, rider, tick, millis, selected))
                views.setOnClickPendingIntent(R.id.zoom_in, RadarTapReceiver.zoom(context, zoomIn = true))
                views.setOnClickPendingIntent(R.id.zoom_out, RadarTapReceiver.zoom(context, zoomIn = false))
                views.setOnClickPendingIntent(R.id.timeline, RadarTapReceiver.step(context))
                emitter.updateView(views)
                tick++
                // ViewEmitter drops updates closer than ~1 s apart
                delay(1_000)
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    companion object {
        const val TYPE_ID = "rain-radar"

        /** Outlives single views: radar keeps loading in the background once the tile was used. */
        val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Position for loading the radar, only with a real GPS fix. */
        fun riderPosition(): Pair<Double, Double>? {
            val data = RideStore.state.value
            if (!data.hasFix) return null
            return data.latitude?.let { lat -> data.longitude?.let { lat to it } }
        }
    }
}

/** Receives taps on the radar tile: +/- zoom, the timeline steps one frame further. The next frame already shows the change. */
class RadarTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ZOOM_IN -> RadarStore.change(context, zoomIn = true)
            ACTION_ZOOM_OUT -> RadarStore.change(context, zoomIn = false)
            ACTION_STEP -> RadarStore.step()
        }
    }

    companion object {
        private const val ACTION_ZOOM_IN = "de.karoofun.karoo.RADAR_ZOOM_IN"
        private const val ACTION_ZOOM_OUT = "de.karoofun.karoo.RADAR_ZOOM_OUT"
        private const val ACTION_STEP = "de.karoofun.karoo.RADAR_STEP"

        fun zoom(context: Context, zoomIn: Boolean) = broadcast(context, if (zoomIn) 1 else 2, Intent(if (zoomIn) ACTION_ZOOM_IN else ACTION_ZOOM_OUT))

        fun step(context: Context) = broadcast(context, 3, Intent(ACTION_STEP))

        private fun broadcast(context: Context, code: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(context, code, intent.setClass(context, RadarTapReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
