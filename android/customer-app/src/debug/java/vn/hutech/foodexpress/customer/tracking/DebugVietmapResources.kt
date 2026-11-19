package vn.hutech.foodexpress.customer.tracking

import android.content.Context
import android.net.Uri
import vn.hutech.foodexpress.customer.BuildConfig
import vn.vietmap.vietmapsdk.Vietmap
import vn.vietmap.vietmapsdk.log.Logger
import vn.vietmap.vietmapsdk.module.http.HttpRequestUtil

internal object DebugVietmapResources {
    const val LogTag = "TrackingVietmap"
    private const val StreetStyle = "https://maps.vietmap.vn/maps/styles/tm/style.json"

    val isConfigured: Boolean get() = BuildConfig.VIETMAP_TILEMAP_KEY.isNotBlank()

    fun streetStyleUri(): String = Uri.parse(StreetStyle).buildUpon()
        .appendQueryParameter("apikey", BuildConfig.VIETMAP_TILEMAP_KEY)
        .build()
        .toString()

    fun initialize(context: Context) {
        Logger.setVerbosity(Logger.NONE)
        Vietmap.getInstance(context.applicationContext)
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
    }
}
