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

/** One comic field as its own data type, for building pages from single fields (e.g. 2 × 4). */
class FieldDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
    private val field: ComicField,
    private val settings: () -> FunSettings,
) : DataTypeImpl(extension, field.typeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val rider = karooSystem.rider()
            RideStore.state.mapNotNull { field.value(it, rider, settings()) }.distinctUntilChanged().collect {
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to it))))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // The field draws its own name, so the standard header would only waste space
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val (width, height) = config.viewSize
        val job = CoroutineScope(Dispatchers.IO).launch {
            val rider = karooSystem.rider()
            val renderer = FieldRenderer(context.fieldLabels(), context.dashboardTexts(), rider.units)
            var frame = 0
            while (isActive) {
                val data = if (config.preview) DemoRide.snapshot(DemoRide.Scenario.NORMAL, frame) else RideStore.state.value
                val views = RemoteViews(context.packageName, R.layout.field_view)
                views.setImageViewBitmap(R.id.field_image, renderer.render(width, height, field, data, rider, FunSettings.load(context), frame))
                emitter.updateView(views)
                frame++
                // ViewEmitter drops updates closer than ~1 s apart
                delay(1_000)
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
