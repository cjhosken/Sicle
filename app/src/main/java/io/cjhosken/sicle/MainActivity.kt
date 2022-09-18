package io.cjhosken.sicle

import android.Manifest
import android.animation.Animator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.layers.properties.generated.ProjectionName
import com.mapbox.maps.extension.style.projection.generated.projection
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.RouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLine
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import io.cjhosken.sicle.helpers.BitmapHelper.Companion.bitmapFromDrawableRes
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import io.cjhosken.sicle.helpers.MainViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    lateinit var mapView: MapView
    private lateinit var location: Point
    private var isNavigating = false


    private lateinit var mapboxNavigation : MapboxNavigation
    private lateinit var navigationLocationProvider: NavigationLocationProvider
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var routeLineApi: MapboxRouteLineApi

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                val query: String = data?.getStringExtra("query").toString()
                Log.d("QUERY", query)

                val geocoding: MapboxGeocoding = MapboxGeocoding.builder()
                    .accessToken(getString(R.string.mapbox_access_token))
                    .query(query)
                    .proximity(location)
                    .build()

                geocoding.enqueueCall(object : Callback<GeocodingResponse> {
                    override fun onResponse(
                        call: Call<GeocodingResponse>,
                        response: Response<GeocodingResponse>
                    ) {
                        val results = response.body()!!.features()
                        if (results.size > 0) {
                            val locationSearchButton: FloatingActionButton =
                                findViewById(R.id.location_search_button)
                            locationSearchButton.setImageDrawable(
                                ResourcesCompat.getDrawable(
                                    resources,
                                    R.drawable.ic_outline_close_24,
                                    null
                                )
                            )
                            isNavigating = true

                            val firstResultPoint = results[0].center()!!
                            addAnnotationToMap(firstResultPoint)

                            fetchARoute(firstResultPoint)
                        }
                    }

                    override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {

                    }
                })

            }
        }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }

    private val defaultOnIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        location = it
    }

    private val routeOnPositionChangedListener = OnIndicatorPositionChangedListener { point ->
        val result = routeLineApi.updateTraveledRouteLine(point)
        mapView.getMapboxMap().getStyle()?.apply {
            routeLineView.renderRouteLineUpdate(this, result)
        }
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private val animatorLister = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {}

        override fun onAnimationCancel(animation: Animator?) {}

        override fun onAnimationEnd(animation: Animator?) {
            val recenterButton: ImageButton = findViewById(R.id.recenter_location_button)
            recenterButton.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_baseline_my_location_28,
                    null
                )
            )
            mapView.location.addOnIndicatorPositionChangedListener(
                onIndicatorPositionChangedListener
            )
        }

        override fun onAnimationRepeat(animation: Animator?) {}
    }

    private val onSearchButtonClickListener = View.OnClickListener {
        val locationSearchButton: FloatingActionButton = findViewById(R.id.location_search_button)
        if (isNavigating) {
            clearRoutes()

            removeAnnotations()

            locationSearchButton.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_baseline_search_24,
                    null
                )
            )
            isNavigating = false
        } else {
            val intent = Intent(this@MainActivity, SearchActivity::class.java)
            resultLauncher.launch(intent)
        }
    }

    private val routesObserver: RoutesObserver = RoutesObserver { routeUpdateResult ->
        val routeLines = routeUpdateResult.routes.map { RouteLine(it, null) }

        routeLineApi.setRoutes(
            routeLines
        ) { value ->
            mapView.getMapboxMap().getStyle()?.apply {
                routeLineView.renderRouteDrawData(this, value)
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        routeLineApi.updateWithRouteProgress(routeProgress) { result ->
            mapView.getMapboxMap().getStyle()?.apply {
                routeLineView.renderRouteLineUpdate(this, result)
            }
        }

    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                enhancedLocation,
                locationMatcherResult.keyPoints,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        addInitialDataListener()
        hideSystemBars()
        setContentView(R.layout.activity_main)

        init()
    }

    private fun addInitialDataListener() {
        val content: View = findViewById(android.R.id.content)

        content.viewTreeObserver.addOnPreDrawListener {
            return@addOnPreDrawListener viewModel.isAppReady.value ?:               false
        }
    }

    private fun init() {
        initMap()
        initLocation()
        initRoutes()
        initLayout()
    }



    private fun initMap() {
        mapView = findViewById(R.id.map_view)
        mapView.getMapboxMap().loadStyle(style("mapbox://styles/cjhosken/ckvxagnw91lob15tbq6kbabyx"){
            +projection(ProjectionName.GLOBE)
        })

        mapView.compass.enabled = true
        mapView.compass.fadeWhenFacingNorth = false
        mapView.compass.marginTop = 16f
        mapView.compass.marginRight = 16f
        mapView.compass.image =
            ResourcesCompat.getDrawable(resources, R.drawable.custom_compass, null)

        mapView.scalebar.enabled = false
        mapView.logo.marginBottom = -100f
        mapView.attribution.marginBottom = -100f

        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(14.0)
                .bearing(0.0)
                .pitch(30.0)
                .build()
        )
    }

    private fun initLocation() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.locationPuck = LocationPuck2D(
                topImage = ResourcesCompat.getDrawable(resources, R.drawable.location_puck, null),
                scaleExpression = "1"
            )

            this.enabled = true
            this.pulsingEnabled = true
            this.pulsingColor = Color.parseColor("#FF6600")
            this.pulsingMaxRadius = 20f
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(
            defaultOnIndicatorPositionChangedListener
        )
        locationComponentPlugin.addOnIndicatorPositionChangedListener(routeOnPositionChangedListener)
        locationComponentPlugin.addOnIndicatorPositionChangedListener(
            onIndicatorPositionChangedListener
        )
        mapView.gestures.addOnMoveListener(onMoveListener)

        mapView.getMapboxMap().getStyle()?.apply {
            routeLineView.hideAlternativeRoutes(this)
        }
    }

    private fun initRoutes() {
        navigationLocationProvider = NavigationLocationProvider()

        mapboxNavigation = when (MapboxNavigationProvider.isCreated()) {
            true -> MapboxNavigationProvider.retrieve()
            false -> MapboxNavigationProvider.create(
                NavigationOptions.Builder(this)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            )
        }

        val routeLineResources = RouteLineResources.Builder()
            .routeLineColorResources(RouteLineColorResources.Builder()
                .routeDefaultColor(getColor(R.color.primary))
                .routeCasingColor(getColor(R.color.accent))
                .build())
            .build()

        val options = MapboxRouteLineOptions.Builder(this)
            .withVanishingRouteLineEnabled(true)
            .withRouteLineResources(routeLineResources)
            .withRouteLineBelowLayerId("road-label-simple")
            .build()

        routeLineView = MapboxRouteLineView(options)

        mapView.getMapboxMap().getStyle()?.apply {
            routeLineView.showPrimaryRoute(this)
            routeLineView.hideOriginAndDestinationPoints(this)
            routeLineView.hideAlternativeRoutes(this)
            routeLineView.hideTraffic(this)
        }

        routeLineApi = MapboxRouteLineApi(options)
    }

    private fun clearRoutes() {

        mapView.getMapboxMap().getStyle()?.apply {
            routeLineView.hidePrimaryRoute(this)
            routeLineView.hideOriginAndDestinationPoints(this)
            routeLineView.hideAlternativeRoutes(this)
            routeLineView.hideTraffic(this)
        }


    }

    private fun initLayout() {
        val recenterButton: ImageButton = findViewById(R.id.recenter_location_button)
        val locationSearchButton: FloatingActionButton = findViewById(R.id.location_search_button)

        recenterButton.setOnClickListener {
            mapView.getMapboxMap().flyTo(
                cameraOptions {
                    center(location)
                    bearing(0.0)
                    zoom(14.0)
                },
                mapAnimationOptions {
                    duration(3000)
                    animatorListener(animatorLister)
                }
            )
        }

        locationSearchButton.setOnClickListener(onSearchButtonClickListener)
    }

    private fun onCameraTrackingDismissed() {
        mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)

        val recenterButton: ImageButton = findViewById(R.id.recenter_location_button)
        recenterButton.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_baseline_location_searching_28,
                null
            )
        )

    }

    private fun hideSystemBars() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun addAnnotationToMap(point: Point) {
        bitmapFromDrawableRes(
            this@MainActivity,
            R.drawable.marker_icon
        )?.let {
            val annotationApi = mapView.annotations
            val pointAnnotationManager = annotationApi.createPointAnnotationManager()
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(it)
            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }

    private fun removeAnnotations() {
        mapView.annotations.cleanup()
    }

    private fun fetchARoute(point: Point) {
        initRoutes()
        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(listOf(location, point))
            .alternatives(false)
            .build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : RouterCallback {
                override fun onRoutesReady(
                    routes: List<DirectionsRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    mapboxNavigation.run {
                        setRoutes(routes)
                        registerRoutesObserver(routesObserver)
                        registerLocationObserver(locationObserver)
                        registerRouteProgressObserver(routeProgressObserver)

                        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            startTripSession()
                        }
                    }
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {

                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {

                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxNavigation.run {
            stopTripSession()
            unregisterRoutesObserver(routesObserver)
            unregisterLocationObserver(locationObserver)
            unregisterRouteProgressObserver(routeProgressObserver)
        }
        routeLineView.cancel()
        routeLineApi.cancel()
        mapboxNavigation.onDestroy()
    }
}