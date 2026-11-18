package de.karoofun.karoo

import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, "fun-dashboard") {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            RideStore.state.mapNotNull { it.distance }.distinctUntilChanged().collect {
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to it))))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // The page draws its own labels, so the standard header would only waste space
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val (width, height) = config.viewSize
        if (!config.preview) RideStore.visibleViews.incrementAndGet()
        val job = CoroutineScope(Dispatchers.IO).launch {
            val rider = karooSystem.rider()
            val texts = context.localized()
            val renderer = DashboardRenderer(texts.dashboardTexts(), rider.units)
            val art = TakeoverArt(texts.takeoverTexts())
            val frames = FrameStore(context, art, texts.language)
            if (!config.preview) launch(Dispatchers.Default) { prepareFrames(context, frames, width, height) }

            var frame = 0
            var shown: ActiveTakeover? = null
            while (isActive) {
                val takeover = RideStore.takeover.value
                if (!config.preview && takeover != null && takeover != shown) {
                    shown = takeover
                    val settings = FunSettings.load(context)
                    val data = RideStore.state.value
                    val count = settings.durationSeconds * TakeoverArt.FPS
                    val views = RemoteViews(context.packageName, R.layout.takeover_view)
                    for (uri in frames.frames(takeover.type, takeover.variant, width, height, count)) {
                        val child = RemoteViews(context.packageName, R.layout.takeover_frame)
                        child.setImageViewUri(R.id.frame_image, uri)
                        views.addView(R.id.frames, child)
                    }
                    views.setImageViewBitmap(R.id.panel, art.renderPanel(takeover.type, width, texts.panelInfo(takeover, data, settings, rider.units)))
                    emitter.updateView(views)
                    // The flipper animates on its own in the Karoo app; we only wait until it is over
                    delay(settings.durationSeconds * 1_000L)
                    RideStore.done(takeover)
                    continue
                }
                val data = if (config.preview) DemoRide.snapshot(DemoRide.Scenario.NORMAL, frame) else RideStore.state.value
                val zone = rider.zone(data.heartRate, FunSettings.load(context)) ?: 1
                val views = RemoteViews(context.packageName, R.layout.dashboard_view)
                views.setImageViewBitmap(R.id.dashboard_image, renderer.render(width, height, data, zone, frame))
                emitter.updateView(views)
                frame++
                // ViewEmitter drops updates closer than ~1 s apart
                delay(1_000)
            }
        }
        emitter.setCancellable {
            if (!config.preview) RideStore.visibleViews.decrementAndGet()
            job.cancel()
        }
    }

    /** Renders all takeover frames for this page size up front, so a takeover can start without delay. */
    private fun prepareFrames(context: Context, frames: FrameStore, width: Int, height: Int) {
        val count = FunSettings.load(context).durationSeconds * TakeoverArt.FPS
        frames.keepOnly(width, height, count)
        for (type in Takeover.entries) {
            val variants = if (type == Takeover.POKAL) context.resources.getStringArray(R.array.pokal_sayings).size else 1
            for (variant in 0 until variants) frames.frames(type, variant, width, height, count)
        }
    }
}
