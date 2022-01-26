package com.ahandyapp.airnavx

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.SettingsSlicesContract.KEY_LOCATION
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ahandyapp.airnavx.ui.sense.SoundMeter
import com.google.android.gms.common.internal.Preconditions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private var map: GoogleMap? = null
    private var cameraPosition: CameraPosition? = null
    private var currlat = 0.0
    private var currlong = 0.0
    private var FLAG = true
    private var timer: CountDownTimer? = null

    // The entry point to the Places API.
    private lateinit var placesClient: PlacesClient
    private lateinit var arr: List<Double>
    var decibel: Double = 0.0

    // The entry point to the Fused Location Provider.
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private val defaultLocation = LatLng(-33.8523341, 151.2106085)
    private var locationPermissionGranted = false

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private var lastKnownLocation: Location? = null
    private var likelyPlaceNames: Array<String?> = arrayOfNulls(0)
    private var likelyPlaceAddresses: Array<String?> = arrayOfNulls(0)
    private var likelyPlaceAttributions: Array<List<*>?> = arrayOfNulls(0)
    private var likelyPlaceLatLngs: Array<LatLng?> = arrayOfNulls(0)

    var handler: Handler = Handler()
    var runnable: Runnable? = null
    var delay = 15000

    private var soundMeter = SoundMeter()

    private lateinit var coordins: List<Double>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }


        setContentView(R.layout.activity_maps)
        setCircleFromDatabase()

//        var database = FirebaseDatabase.getInstance().reference.child("points")
//        database.child(database.push().key ?: "blablabla").setValue(Points(currlat, currlong, decibel))


        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        placesClient = Places.createClient(this)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        soundMeter.start(this)

        val fab = findViewById<FloatingActionButton>(R.id.fab_point)
        fab.setOnClickListener { view ->
            Toast.makeText(this, "Отслеживание начато", Toast.LENGTH_SHORT).show()
            onResume()
        }


        val fabInfo = findViewById<FloatingActionButton>(R.id.advice)
        fabInfo.setOnClickListener { view ->
            val intent = Intent(this, FabInfoActivity::class.java);
            startActivity(intent)
        }
    }

    override fun onResume() {
        handler.postDelayed(Runnable {
            handler.postDelayed(runnable!!, delay.toLong())
            addCircles()
        }.also { runnable = it }, delay.toLong())
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable!!)
    }

    private fun setCircleFromDatabase() {
        val database2 = Firebase.database
        val myRef2 = database2.getReference("points")
        arr = listOf<Double>()
        myRef2.get().addOnSuccessListener {
            val map = (it.value as Map<String, Objects>)
            for (entry in map.entries){
                var eMap = entry.value as  Map<String, Objects>
                var entr = eMap.entries as Set

                var dbCoord = entr.elementAt(1).toString().replace("db=", "", true).toDouble()
                var lngCoord = entr.first().toString().replace("lng=", "", true).toDouble()
                var latCoord = entr.last().toString().replace("lat=", "", true).toDouble()

                val coordins: List<Double> = listOf(latCoord,lngCoord, dbCoord)
                arr += coordins
            }

//            Log.i("firebase", "$arr")

        }.addOnFailureListener{
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        map?.let { map ->
            outState.putParcelable(KEY_CAMERA_POSITION, map.cameraPosition)
            outState.putParcelable(KEY_LOCATION, lastKnownLocation)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.current_place_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.info) {
            val intent = Intent(this, MainActivity::class.java);
            startActivity(intent);
        }
        if (item.itemId == R.id.noisemets) {
            addCircles()
        }
        return true
    }

    private fun addCircles() {
        getDataForCircle()

        val database = Firebase.database
        val myRef = database.getReference("points")
        myRef.child(myRef.push().key ?: "blablabla").setValue(Points(currlat,currlong,decibel))
        val value = 20.0

        var elem = arr.size -1


        for ( i in elem downTo 0 step 3){

            Log.i("firebase", " aaaa ${i}")
            Log.i("firebase", " aaaa ${arr[i]}")

//            Log.i("firebase", " aaaaaaaaaa ${arr[i]}")
//            Log.i("firebase", " lat ${arr[i -2]}")
//            Log.i("firebase", " lng ${arr[i-1]}")

            if (arr[i] == 0.0) {
                val circle = map!!.addCircle(
                    CircleOptions()
                        .center(LatLng(arr[i -2], arr[i-1]))
                        .radius(10.0)
                        .strokeWidth(1f)
                        .strokeColor(Color.TRANSPARENT)
                        .fillColor(Color.TRANSPARENT)
                        .clickable(true)
                )
            }
            if (arr[i] <= 40.0 && arr[i] != 0.0) {
                val circle = map!!.addCircle(
                    CircleOptions()
                        .center(LatLng(arr[i-2], arr[i-1]))
                        .radius(value)
                        .strokeWidth(10f)
                        .strokeColor(Color.argb(27, 0, 255, 10))
                        .fillColor(Color.argb(27, 0, 255, 10))
                        .clickable(true)
                )
            }

            if (arr[i]  > 40.0 && arr[i] <= 80.0) {
                val circle = map!!.addCircle(
                    CircleOptions()
                        .center(LatLng(arr[i-2], arr[i-1]))
                        .radius(value)
                        .strokeWidth(10f)
                        .strokeColor(Color.argb(33, 255, 229, 0))
                        .fillColor(Color.argb(33, 255, 229, 0))
                        .clickable(true)
                )
            }
            if (arr[i] > 80.0 && arr[i]<= 120.0) {
                val circle = map!!.addCircle(
                    CircleOptions()
                        .center(LatLng(arr[i-2], arr[i-1]))
                        .radius(value)
                        .strokeWidth(10f)
                        .strokeColor(Color.argb(37, 255, 153, 0))
                        .fillColor(Color.argb(37, 255, 153, 0))
                        .clickable(true)
                )
            }
            if (arr[i] > 120.0) {
                val circle = map!!.addCircle(
                    CircleOptions()
                        .center(LatLng(arr[i-2], arr[i-1]))
                        .radius(value)
                        .strokeWidth(10f)
                        .strokeColor(Color.argb(45, 255, 61, 0))
                        .fillColor(Color.argb(45, 255, 61, 0))
                        .clickable(true)
                )
            }
        }
        if (decibel == 0.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(1.0)
                    .strokeWidth(1f)
                    .strokeColor(Color.TRANSPARENT)
                    .fillColor(Color.TRANSPARENT)
                    .clickable(true)
            )
        }
        if (decibel <= 40.0 && decibel != 0.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.argb(27, 0, 255, 10))
                    .fillColor(Color.argb(27, 0, 255, 10))
                    .clickable(true)
            )
        }
        if (decibel > 40.0 && decibel <= 80.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.argb(33, 255, 229, 0))
                    .fillColor(Color.argb(33, 255, 229, 0))
                    .clickable(true)
            )
        }
        if (decibel > 80.0 && decibel <= 120.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.argb(37, 255, 153, 0))
                    .fillColor(Color.argb(37, 255, 153, 0))
                    .clickable(true)
            )
        }
        if (decibel > 120.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.argb(45, 255, 61, 0))
                    .fillColor(Color.argb(45, 255, 61, 0))
                    .clickable(true)
            )
        }
        map!!.setOnCircleClickListener {
            // Flip the r, g and b components of the circle's stroke color.
            val strokeColor = it.strokeColor xor 0x00ffffff
            it.strokeColor = strokeColor
        }
    }



    private fun getDataForCircle() {
        getDeviceLocation()
        currlat = lastKnownLocation!!.latitude
        currlong = lastKnownLocation!!.longitude
        decibel = soundMeter.deriveDecibel(forceFormat = true)
    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.mapstyle_night));

        this.map?.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View {
                // Inflate the layouts for the info window, title and snippet.
                val infoWindow = layoutInflater.inflate(
                    R.layout.custom_info_contents,
                    findViewById<FrameLayout>(R.id.map), false
                )
                val title = infoWindow.findViewById<TextView>(R.id.title)
                title.text = marker.title
                val snippet = infoWindow.findViewById<TextView>(R.id.snippet)
                snippet.text = marker.snippet
                return infoWindow
            }
        })
        getLocationPermission()
        updateLocationUI()
        getDeviceLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        lastKnownLocation = task.result
//                        if (lastKnownLocation != null) {
//                            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
//                                LatLng(lastKnownLocation!!.latitude,
//                                    lastKnownLocation!!.longitude), DEFAULT_ZOOM.toFloat()))
//                        }
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        Log.e(TAG, "Exception: %s", task.exception)
//                        map?.moveCamera(CameraUpdateFactory
//                            .newLatLngZoom(defaultLocation, DEFAULT_ZOOM.toFloat()))
                        map?.uiSettings?.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        locationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    locationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }

    @SuppressLint("MissingPermission")
    private fun showCurrentPlace() {
        if (map == null) {
            return
        }
        if (locationPermissionGranted) {
            val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
            val request = FindCurrentPlaceRequest.newInstance(placeFields)

            val placeResult = placesClient.findCurrentPlace(request)
            placeResult.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val likelyPlaces = task.result
                    val count =
                        if (likelyPlaces != null && likelyPlaces.placeLikelihoods.size < M_MAX_ENTRIES) {
                            likelyPlaces.placeLikelihoods.size
                        } else {
                            M_MAX_ENTRIES
                        }
                    var i = 0
                    likelyPlaceNames = arrayOfNulls(count)
                    likelyPlaceAddresses = arrayOfNulls(count)
                    likelyPlaceAttributions = arrayOfNulls<List<*>?>(count)
                    likelyPlaceLatLngs = arrayOfNulls(count)
                    for (placeLikelihood in likelyPlaces?.placeLikelihoods ?: emptyList()) {
                        // Build a list of likely places to show the user.
                        likelyPlaceNames[i] = placeLikelihood.place.name
                        likelyPlaceAddresses[i] = placeLikelihood.place.address
                        likelyPlaceAttributions[i] = placeLikelihood.place.attributions
                        likelyPlaceLatLngs[i] = placeLikelihood.place.latLng
                        i++
                        if (i > count - 1) {
                            break
                        }
                    }
                    openPlacesDialog()
                } else {
                    Log.e(TAG, "Exception: %s", task.exception)
                }
            }
        } else {
            Log.i(TAG, "The user did not grant location permission.")

            map?.addMarker(
                MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(defaultLocation)
                    .snippet(getString(R.string.default_info_snippet))
            )
            getLocationPermission()
        }
    }

    private fun openPlacesDialog() {
        val listener =
            DialogInterface.OnClickListener { dialog, which ->
                val markerLatLng = likelyPlaceLatLngs[which]
                var markerSnippet = likelyPlaceAddresses[which]
                if (likelyPlaceAttributions[which] != null) {
                    markerSnippet = """
                    $markerSnippet
                    ${likelyPlaceAttributions[which]}
                    """.trimIndent()
                }
                if (markerLatLng == null) {
                    return@OnClickListener
                }
                map?.addMarker(
                    MarkerOptions()
                        .title(likelyPlaceNames[which])
                        .position(markerLatLng)
                        .snippet(markerSnippet)
                )
                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        markerLatLng,
                        DEFAULT_ZOOM.toFloat()
                    )
                )
                Toast.makeText(this, "message", Toast.LENGTH_SHORT).show()
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_place)
            .setItems(likelyPlaceNames, listener)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        if (map == null) {
            return
        }
        try {
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                lastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 19
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"

        private const val M_MAX_ENTRIES = 5
    }
}
