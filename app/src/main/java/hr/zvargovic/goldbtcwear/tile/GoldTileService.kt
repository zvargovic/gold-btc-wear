package hr.zvargovic.goldbtcwear.tile

import android.content.Context
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.ResourceBuilders as TileResBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// ProtoLayout
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
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

    // helpers
    private fun c(argb: Int) = ColorBuilders.argb(argb)
    private fun dp(v: Float) = DimensionBuilders.dp(v)
    private fun sp(v: Float) = DimensionBuilders.SpProp.Builder().setValue(v).build()

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {

        // --- Data (kratko blocking čitanje iz DataStore-a) ---
        val store = SpotStore(applicationContext)
        val spot: Double? = runBlocking { store.lastSpotFlow.firstOrNull() }
        val ref : Double? = runBlocking { store.refSpotFlow.firstOrNull() }

        val spotTxt = spot?.let { "€" + "%,.2f".format(Locale.US, it) } ?: "--"
        val deltaTxt = if (spot != null && ref != null && ref > 0.0) {
            val pct = ((spot - ref) / ref * 100.0).coerceIn(-50.0, 50.0)
            String.format(Locale.US, "%+.1f%%", pct)
        } else " "

        val deltaIsPos = deltaTxt.trim().startsWith("+")

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

        // Layout
        val layout = buildTileLayout(
            spotTxt = spotTxt,
            deltaTxt = deltaTxt,
            deltaIsPos = deltaIsPos,
            click = clickable
        )

        val timeline = ProtoTL.Timeline.Builder()
            .addTimelineEntry(
                ProtoTL.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline)
            // traži svježe podatke barem svake minute kad je tile aktivan
            .setFreshnessIntervalMillis(60_000)
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

    /** Weather-style kartica s tvojim bojama. */
    private fun buildTileLayout(
        spotTxt: String,
        deltaTxt: String,
        deltaIsPos: Boolean,
        click: ModifiersBuilders.Clickable
    ): LayoutElementBuilders.Layout {
        val cardBg   = c(0xFF121212.toInt())            // tamna kartica
        val titleCol = c(0xFFFF7A00.toInt())            // narančasta (brand)
        val priceCol = c(0xFFEDE7DE.toInt())            // toplo svijetla
        val deltaCol = if (deltaIsPos) c(0xFF38D66B.toInt()) else c(0xFFF05454.toInt())

        val title = LayoutElementBuilders.Text.Builder()
            .setText("GOLD")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(12f))
                    .setWeight(
                        LayoutElementBuilders.FontWeightProp.Builder()
                            .setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .setColor(titleCol)
                    .build()
            )
            .build()

        val price = LayoutElementBuilders.Text.Builder()
            .setText(spotTxt)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(26f))
                    .setWeight(
                        LayoutElementBuilders.FontWeightProp.Builder()
                            .setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .setColor(priceCol)
                    .build()
            )
            .build()

        val delta = LayoutElementBuilders.Text.Builder()
            .setText(deltaTxt)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(14f))
                    .setColor(deltaCol)
                    .build()
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .addContent(title)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(price)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(delta)
            .build()

        val cardBox = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(cardBg)
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(18f))
                                    .build()
                            )
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(14f))
                            .build()
                    )
                    .setClickable(click)
                    .build()
            )
            .addContent(column)
            .build()

        return LayoutElementBuilders.Layout.Builder()
            .setRoot(
                LayoutElementBuilders.Box.Builder()
                    .addContent(cardBox)
                    .build()
            )
            .build()
    }

    companion object {
        fun requestUpdate(context: Context) {
            // odmah poguraj refresh kad ima nova cijena
            TileService.getUpdater(context).requestUpdate(GoldTileService::class.java)
        }
    }
}