package hr.zvargovic.goldbtcwear.tile
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.content.Context
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders as TileResBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders as ProtoTL


import hr.zvargovic.goldbtcwear.data.SpotStore
import hr.zvargovic.goldbtcwear.presentation.MainActivity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.Locale

class GoldTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {

        // --- data (short blocking read from DataStore) ---
        val store = SpotStore(applicationContext)
        val spot: Double? = runBlocking { store.lastSpotFlow.firstOrNull() }
        val ref : Double? = runBlocking { store.refSpotFlow.firstOrNull() }

        val spotTxt = spot?.let { "€" + "%,.2f".format(Locale.US, it) } ?: "--"
        val deltaTxt = if (spot != null && ref != null && ref > 0.0) {
            val pct = ((spot - ref) / ref * 100.0).coerceIn(-50.0, 50.0)
            String.format(Locale.US, "%+.1f%%", pct)
        } else " "

        // tap → open app
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        // --- UI (ProtoLayout) ---
        val title = LayoutElementBuilders.Text.Builder()
            .setText("GOLD")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.SpProp.Builder().setValue(12f).build())
                    .build()
            )
            .build()

        val price = LayoutElementBuilders.Text.Builder()
            .setText(spotTxt)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.SpProp.Builder().setValue(20f).build())
                    .setWeight(
                        LayoutElementBuilders.FontWeightProp.Builder()
                            .setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD) // 700
                            .build()
                    )
                    .build()
            )
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .build()

        val delta = LayoutElementBuilders.Text.Builder()
            .setText(deltaTxt)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.SpProp.Builder().setValue(12f).build())
                    .build()
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .addContent(title)
            .addContent(price)
            .addContent(delta)
            .build()

        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(column) // ProtoLayout root
            .build()

        val timeline = ProtoTL.Timeline.Builder()
            .addTimelineEntry(
                ProtoTL.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline) // expects ProtoLayout timeline (fixed)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<TileResBuilders.Resources> {
        val res = TileResBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return Futures.immediateFuture(res)
    }



    companion object {
        fun requestUpdate(context: Context) {
            TileService.getUpdater(context)
                .requestUpdate(GoldTileService::class.java)
        }
    }
}