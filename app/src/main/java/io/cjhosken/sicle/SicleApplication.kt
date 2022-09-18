package io.cjhosken.sicle

import android.app.Application
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.search.MapboxSearchSdk


class SicleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        MapboxSearchSdk.initialize(
            this,
            getString(R.string.mapbox_access_token),
            locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        )
    }
}
