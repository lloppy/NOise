package com.ahandyapp.airnavx

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private var map: GoogleMap? = null
    private var cameraPosition: CameraPosition? = null
    private var currlat = 0.0
    private var currlong = 0.0

    // The entry point to the Places API.
    private lateinit var placesClient: PlacesClient

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

    private var soundMeter = SoundMeter()

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }

        setContentView(R.layout.activity_maps)



        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        placesClient = Places.createClient(this)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        soundMeter.start(this)

        val fab = findViewById<FloatingActionButton>(R.id.fab_point)
        fab.setOnClickListener { view ->
            addCircle()
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
            addCircle()
        }
        return true
    }

    private fun addCircle() {

        getDeviceLocation()

        val decibel = soundMeter.deriveDecibel(forceFormat = true)
        Log.e("DECIBEL", decibel.toString())

        currlat = lastKnownLocation!!.latitude
        currlong = lastKnownLocation!!.longitude
        val value = 10.0


        database = Firebase.database.reference.child("points")
        database.child(database.push().key ?: "blablabla").child("points").setValue("$currlat $currlong")

        database.get().addOnSuccessListener {
            Log.i("firebase", "Got value ${it.value}")
        }.addOnFailureListener{
            Log.e("firebase", "Error getting data", it)
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
                    .strokeColor(Color.GREEN)
                    .fillColor(Color.GREEN)
                    .clickable(true)
            )
        }

        if (decibel > 40.0 && decibel <= 80.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.YELLOW)
                    .fillColor(Color.YELLOW)
                    .clickable(true)
            )
        }

        if (decibel > 80.0 && decibel <= 120.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.RED)
                    .fillColor(Color.RED)
                    .clickable(true)
            )
        }

        if (decibel > 120.0) {
            val circle = map!!.addCircle(
                CircleOptions()
                    .center(LatLng(currlat, currlong))
                    .radius(value)
                    .strokeWidth(10f)
                    .strokeColor(Color.BLACK)
                    .fillColor(Color.BLACK)
                    .clickable(true)
            )
        }



        map!!.setOnCircleClickListener {
            // Flip the r, g and b components of the circle's stroke color.
            val strokeColor = it.strokeColor xor 0x00ffffff
            it.strokeColor = strokeColor
        }
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
        // [END map_current_place_set_info_window_adapter]

        // Prompt the user for permission.
        getLocationPermission()
        // [END_EXCLUDE]

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI()

        // Get the current location of the device and set the position of the map.
        getDeviceLocation()
    }
    // [END maps_current_place_on_map_ready]

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    // [START maps_current_place_get_device_location]
    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Set the map's camera position to the current location of the device.
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
    // [END maps_current_place_get_device_location]


    val building1 = map?.addPolygon(
        PolygonOptions().clickable(true)

            .add(LatLng(	43.4310672	,	39.938954	),
                LatLng(	43.4312221	,	39.9398263	),
                LatLng(	43.4313127	,	39.9403265	),
                LatLng(	43.4314007	,	39.9408373	),
                LatLng(	43.4315776	,	39.9415308	),
                LatLng(	43.431898	,	39.942656	),
                LatLng(	43.431973	,	39.9429215	),
                LatLng(	43.4320529	,	39.9431562	),
                LatLng(	43.4322496	,	39.9436766	),
                LatLng(	43.4327989	,	39.9447079	),
                LatLng(	43.4331826	,	39.9453785	),
                LatLng(	43.4333861	,	39.9457017	),
                LatLng(	43.4338857	,	39.9464138	),
                LatLng(	43.4340844	,	39.9466619	),
                LatLng(	43.4346044	,	39.9472707	),
                LatLng(	43.4349823	,	39.9476221	),
                LatLng(	43.4361226	,	39.9486521	),
                LatLng(	43.43663	,	39.9490933	),
                LatLng(	43.4376506	,	39.9499516	),
                LatLng(	43.4381082	,	39.9503244	),
                LatLng(	43.4385182	,	39.9506155	),
                LatLng(	43.4392584	,	39.951156	),
                LatLng(	43.4395056	,	39.9513249	),
                LatLng(	43.4404219	,	39.951923	),
                LatLng(	43.4407306	,	39.952084	),
                LatLng(	43.4408465	,	39.9521403	),
                LatLng(	43.4420393	,	39.9526365	),
                LatLng(	43.4425132	,	39.9528498	),
                LatLng(	43.4431874	,	39.9531461	),
                LatLng(	43.4438719	,	39.9534921	),
                LatLng(	43.4441153	,	39.9536356	),
                LatLng(	43.4451757	,	39.9543022	),
                LatLng(	43.4455515	,	39.954569	),
                LatLng(	43.4465933	,	39.9553965	),
                LatLng(	43.4469117	,	39.9556674	),
                LatLng(	43.4489564	,	39.9575745	),
                LatLng(	43.4506816	,	39.9591824	),
                LatLng(	43.4510389	,	39.9595043	),
                LatLng(	43.4519794	,	39.9603948	),
                LatLng(	43.4529432	,	39.961292	),
                LatLng(	43.4541932	,	39.9624686	),
                LatLng(	43.4545632	,	39.9628168	),
                LatLng(	43.4546148	,	39.9627149	),
                LatLng(	43.4547281	,	39.9627976	),
                LatLng(	43.4547472	,	39.9628115	),
                LatLng(	43.4561374	,	39.9641472	),
                LatLng(	43.4561478	,	39.9641305	),
                LatLng(	43.4566391	,	39.9633395	),
                LatLng(	43.4566027	,	39.9632996	),
                LatLng(	43.4566738	,	39.9631548	),
                LatLng(	43.4566924	,	39.9631213	),
                LatLng(	43.4567673	,	39.9629858	),
                LatLng(	43.4568374	,	39.9628933	),
                LatLng(	43.4563048	,	39.9619786	),
                LatLng(	43.4559768	,	39.9614221	),
                LatLng(	43.4555961	,	39.9607379	),
                LatLng(	43.4554881	,	39.9605437	),
                LatLng(	43.4554667	,	39.9604592	),
                LatLng(	43.4553642	,	39.9601611	),
                LatLng(	43.4553138	,	39.9599958	),
                LatLng(	43.4552539	,	39.9597209	),
                LatLng(	43.4550802	,	39.9594688	),
                LatLng(	43.4548339	,	39.9591308	),
                LatLng(	43.4547764	,	39.9590557	),
                LatLng(	43.4546937	,	39.9589135	),
                LatLng(	43.45448	,	39.9585488	),
                LatLng(	43.4541753	,	39.9580418	),
                LatLng(	43.4539504	,	39.9576596	),
                LatLng(	43.4536607	,	39.9571869	),
                LatLng(	43.4535483	,	39.9569871	),
                LatLng(	43.4531263	,	39.9561422	),
                LatLng(	43.4528464	,	39.9555782	),
                LatLng(	43.4527541	,	39.9555761	),
                LatLng(	43.4527428	,	39.955553	),
                LatLng(	43.4525465	,	39.9551578	),
                LatLng(	43.4524238	,	39.9549177	),
                LatLng(	43.4522851	,	39.9546455	),
                LatLng(	43.4522549	,	39.9545965	),
                LatLng(	43.4522111	,	39.9545362	),
                LatLng(	43.4522106	,	39.9544953	),
                LatLng(	43.4520081	,	39.9540789	),
                LatLng(	43.4518155	,	39.9537032	),
                LatLng(	43.4516518	,	39.9533714	),
                LatLng(	43.4513383	,	39.9527109	),
                LatLng(	43.4510978	,	39.9521973	),
                LatLng(	43.4509323	,	39.9518667	),
                LatLng(	43.4507765	,	39.9515281	),
                LatLng(	43.450687	,	39.9513068	),
                LatLng(	43.4505609	,	39.9509802	),
                LatLng(	43.450432	,	39.9506575	),
                LatLng(	43.450631	,	39.9499382	),
                LatLng(	43.4508014	,	39.9493749	),
                LatLng(	43.4506105	,	39.9490377	),
                LatLng(	43.4506486	,	39.9488679	),
                LatLng(	43.4506998	,	39.9488437	),
                LatLng(	43.4506993	,	39.9486957	),
                LatLng(	43.4506957	,	39.9483711	),
                LatLng(	43.4506977	,	39.9478193	),
                LatLng(	43.4505414	,	39.94737	),
                LatLng(	43.4503476	,	39.9472515	),
                LatLng(	43.4503084	,	39.9471361	),
                LatLng(	43.4503024	,	39.9471135	),
                LatLng(	43.4502523	,	39.9469247	),
                LatLng(	43.4502439	,	39.9468774	),
                LatLng(	43.4502305	,	39.946828	),
                LatLng(	43.4502143	,	39.9467678	),
                LatLng(	43.4501929	,	39.9467048	),
                LatLng(	43.4501413	,	39.9465521	),
                LatLng(	43.4501551	,	39.9464947	),
                LatLng(	43.4498628	,	39.9456105	),
                LatLng(	43.4496746	,	39.9450485	),
                LatLng(	43.4495999	,	39.944836	),
                LatLng(	43.4497956	,	39.9446958	),
                LatLng(	43.4497314	,	39.9445322	),
                LatLng(	43.4497314	,	39.944374	),
                LatLng(	43.4497762	,	39.9441299	),
                LatLng(	43.4497842	,	39.9440889	),
                LatLng(	43.4498161	,	39.9439247	),
                LatLng(	43.4499796	,	39.94334	),
                LatLng(	43.450004	,	39.9431495	),
                LatLng(	43.4499767	,	39.9429846	),
                LatLng(	43.4499329	,	39.9428464	),
                LatLng(	43.4499816	,	39.9427298	),
                LatLng(	43.4499996	,	39.9426594	),
                LatLng(	43.4497849	,	39.941732	),
                LatLng(	43.4497679	,	39.9416602	),
                LatLng(	43.4492421	,	39.9401019	),
                LatLng(	43.4492236	,	39.9400415	),
                LatLng(	43.4491954	,	39.9399585	),
                LatLng(	43.448932	,	39.9391839	),
                LatLng(	43.4488994	,	39.9391001	),
                LatLng(	43.4487675	,	39.9387138	),
                LatLng(	43.4487411	,	39.9386485	),
                LatLng(	43.4477571	,	39.9362156	),
                LatLng(	43.4477218	,	39.9361154	),
                LatLng(	43.4477106	,	39.9361275	),
                LatLng(	43.4476653	,	39.9360068	),
                LatLng(	43.4476125	,	39.9358723	),
                LatLng(	43.4476012	,	39.9358435	),
                LatLng(	43.4475612	,	39.9357413	),
                LatLng(	43.4472924	,	39.9349782	),
                LatLng(	43.4472408	,	39.9348333	),
                LatLng(	43.4473119	,	39.9347918	),
                LatLng(	43.4472929	,	39.9347187	),
                LatLng(	43.4472676	,	39.934708	),
                LatLng(	43.4472306	,	39.9345919	),
                LatLng(	43.4470996	,	39.9341836	),
                LatLng(	43.4470074	,	39.9338872	),
                LatLng(	43.4469268	,	39.9336056	),
                LatLng(	43.4467946	,	39.9332682	),
                LatLng(	43.4465912	,	39.9327362	),
                LatLng(	43.44653	,	39.9325761	),
                LatLng(	43.4465138	,	39.9325692	),
                LatLng(	43.4464496	,	39.9324051	),
                LatLng(	43.4463553	,	39.9321216	),
                LatLng(	43.4471585	,	39.9316046	),
                LatLng(	43.4471965	,	39.9315785	),
                LatLng(	43.4470731	,	39.9312878	),
                LatLng(	43.4470884	,	39.931269	),
                LatLng(	43.4469972	,	39.9310588	),
                LatLng(	43.4469821	,	39.9310699	),
                LatLng(	43.4468859	,	39.9308375	),
                LatLng(	43.447031	,	39.9307235	),
                LatLng(	43.4470155	,	39.9306877	),
                LatLng(	43.4469552	,	39.930565	),
                LatLng(	43.4465812	,	39.929776	),
                LatLng(	43.4464833	,	39.9295742	),
                LatLng(	43.4462686	,	39.9291337	),
                LatLng(	43.4461717	,	39.9289405	),
                LatLng(	43.4460284	,	39.9286694	),
                LatLng(	43.4457643	,	39.9281339	),
                LatLng(	43.4457394	,	39.9280866	),
                LatLng(	43.4456895	,	39.9279877	),
                LatLng(	43.4455547	,	39.9277117	),
                LatLng(	43.4455062	,	39.9276497	),
                LatLng(	43.4454137	,	39.927474	),
                LatLng(	43.4453295	,	39.9273064	),
                LatLng(	43.445275	,	39.9271904	),
                LatLng(	43.4452424	,	39.9271072	),
                LatLng(	43.4451791	,	39.9269121	),
                LatLng(	43.4450924	,	39.9266845	),
                LatLng(	43.4450829	,	39.9266717	),
                LatLng(	43.4450632	,	39.9266546	),
                LatLng(	43.4450411	,	39.926612	),
                LatLng(	43.4450396	,	39.9265703	),
                LatLng(	43.444878	,	39.926265	),
                LatLng(	43.4448675	,	39.9262571	),
                LatLng(	43.444814	,	39.9262693	),
                LatLng(	43.444609	,	39.9265976	),
                LatLng(	43.4443335	,	39.9270463	),
                LatLng(	43.4443062	,	39.9270117	),
                LatLng(	43.4442973	,	39.927028	),
                LatLng(	43.4436893	,	39.9262942	),
                LatLng(	43.4441003	,	39.9256579	),
                LatLng(	43.4441056	,	39.9256366	),
                LatLng(	43.444109	,	39.9255981	),
                LatLng(	43.4441095	,	39.9255516	),
                LatLng(	43.4441066	,	39.9254791	),
                LatLng(	43.4440992	,	39.9254128	),
                LatLng(	43.4440801	,	39.9253559	),
                LatLng(	43.4440698	,	39.9253252	),
                LatLng(	43.4439985	,	39.9252418	),
                LatLng(	43.4439444	,	39.925174	),
                LatLng(	43.4435739	,	39.9247952	),
                LatLng(	43.4433641	,	39.9245759	),
                LatLng(	43.4432268	,	39.9244076	),
                LatLng(	43.443127	,	39.9242748	),
                LatLng(	43.4429064	,	39.9240354	),
                LatLng(	43.4428471	,	39.9239744	),
                LatLng(	43.4426343	,	39.9237511	),
                LatLng(	43.4421679	,	39.9232539	),
                LatLng(	43.4419478	,	39.9230061	),
                LatLng(	43.4417983	,	39.9228694	),
                LatLng(	43.4417394	,	39.9227943	),
                LatLng(	43.4415797	,	39.9225985	),
                LatLng(	43.4415121	,	39.9225066	),
                LatLng(	43.4414617	,	39.9224376	),
                LatLng(	43.4413368	,	39.9222873	),
                LatLng(	43.4411787	,	39.9220629	),
                LatLng(	43.4410023	,	39.9218897	),
                LatLng(	43.4408808	,	39.9217923	),
                LatLng(	43.4407194	,	39.9216496	),
                LatLng(	43.4406834	,	39.9217287	),
                LatLng(	43.4406344	,	39.9216909	),
                LatLng(	43.4405948	,	39.9216603	),
                LatLng(	43.440623	,	39.9215638	),
                LatLng(	43.4404775	,	39.9214129	),
                LatLng(	43.4403708	,	39.9212761	),
                LatLng(	43.4403216	,	39.9211977	),
                LatLng(	43.4403187	,	39.9211527	),
                LatLng(	43.4402898	,	39.9211534	),
                LatLng(	43.4402395	,	39.9211522	),
                LatLng(	43.4402059	,	39.92115	),
                LatLng(	43.4402009	,	39.9210451	),
                LatLng(	43.4401858	,	39.9210019	),
                LatLng(	43.4401454	,	39.9209268	),
                LatLng(	43.4400977	,	39.9209006	),
                LatLng(	43.4399166	,	39.9207987	),
                LatLng(	43.4398786	,	39.9207866	),
                LatLng(	43.4397627	,	39.9207611	),
                LatLng(	43.4396089	,	39.9207893	),
                LatLng(	43.4395397	,	39.9208255	),
                LatLng(	43.4394092	,	39.9209073	),
                LatLng(	43.4392417	,	39.9210535	),
                LatLng(	43.4391074	,	39.9211675	),
                LatLng(	43.4387685	,	39.921425	),
                LatLng(	43.4387388	,	39.9214586	),
                LatLng(	43.4385646	,	39.9218194	),
                LatLng(	43.4379573	,	39.9231303	),
                LatLng(	43.4373784	,	39.924368	),
                LatLng(	43.4369075	,	39.9253745	),
                LatLng(	43.436468	,	39.9263354	),
                LatLng(	43.4364436	,	39.9263301	),
                LatLng(	43.4361158	,	39.9270342	),
                LatLng(	43.4361976	,	39.9271174	),
                LatLng(	43.4361596	,	39.9271938	),
                LatLng(	43.4361445	,	39.9272261	),
                LatLng(	43.4361275	,	39.9272642	),
                LatLng(	43.4361061	,	39.9272608	),
                LatLng(	43.4360816	,	39.9273042	),
                LatLng(	43.4360326	,	39.9273997	),
                LatLng(	43.4360077	,	39.9274465	),
                LatLng(	43.4359133	,	39.9274412	),
                LatLng(	43.4365964	,	39.9280753	),
                LatLng(	43.4364836	,	39.9283544	),
                LatLng(	43.4362152	,	39.9288329	),
                LatLng(	43.4359333	,	39.9285293	),
                LatLng(	43.4354433	,	39.9293017	),
                LatLng(	43.4354582	,	39.9293594	),
                LatLng(	43.4356771	,	39.9300873	),
                LatLng(	43.4357104	,	39.9301467	),
                LatLng(	43.4357288	,	39.930229	),
                LatLng(	43.4360305	,	39.9312522	),
                LatLng(	43.4362863	,	39.93212	),
                LatLng(	43.4363005	,	39.932162	),
                LatLng(	43.4363131	,	39.9322084	),
                LatLng(	43.4365372	,	39.9329086	),
                LatLng(	43.4366933	,	39.9330912	),
                LatLng(	43.4370224	,	39.9335164	),
                LatLng(	43.4371705	,	39.9337336	),
                LatLng(	43.437486	,	39.9343184	),
                LatLng(	43.4376019	,	39.9345383	),
                LatLng(	43.4376384	,	39.9346318	),
                LatLng(	43.4377305	,	39.9348799	),
                LatLng(	43.4377762	,	39.9350234	),
                LatLng(	43.4377782	,	39.9352736	),
                LatLng(	43.4377654	,	39.9354507	),
                LatLng(	43.4374032	,	39.9370609	),
                LatLng(	43.4373428	,	39.9373265	),
                LatLng(	43.4373039	,	39.9374847	),
                LatLng(	43.4372513	,	39.9374767	),
                LatLng(	43.4370526	,	39.937714	),
                LatLng(	43.4364606	,	39.9384087	),
                LatLng(	43.4363573	,	39.9384959	),
                LatLng(	43.4358902	,	39.9390359	),
                LatLng(	43.4358207	,	39.9391162	),
                LatLng(	43.4356922	,	39.939265	),
                LatLng(	43.4357015	,	39.9392804	),
                LatLng(	43.4356761	,	39.9393113	),
                LatLng(	43.435661	,	39.9392986	),
                LatLng(	43.4354409	,	39.9395594	),
                LatLng(	43.4353786	,	39.9396372	),
                LatLng(	43.4353533	,	39.9396962	),
                LatLng(	43.4352228	,	39.9398316	),
                LatLng(	43.4351498	,	39.9399135	),
                LatLng(	43.4350941	,	39.939964	),
                LatLng(	43.4349472	,	39.9400972	),
                LatLng(	43.4348464	,	39.9402293	),
                LatLng(	43.4347115	,	39.9404056	),
                LatLng(	43.4346336	,	39.940519	),
                LatLng(	43.4344559	,	39.9407704	),
                LatLng(	43.434377	,	39.9408891	),
                LatLng(	43.434285	,	39.9407704	),
                LatLng(	43.434192	,	39.9406564	),
                LatLng(	43.4340274	,	39.9403808	),
                LatLng(	43.4338117	,	39.9400134	),
                LatLng(	43.4337362	,	39.93989	),
                LatLng(	43.4337197	,	39.9398477	),
                LatLng(	43.4337279	,	39.9398142	),
                LatLng(	43.4338083	,	39.9396774	),
                LatLng(	43.4335293	,	39.9392986	),
                LatLng(	43.4334601	,	39.9393039	),
                LatLng(	43.4334386	,	39.9393152	),
                LatLng(	43.4334255	,	39.939322	),
                LatLng(	43.4331714	,	39.9395916	),
                LatLng(	43.4331388	,	39.9396063	),
                LatLng(	43.4325887	,	39.9390985	),
                LatLng(	43.4325808	,	39.9390946	),
                LatLng(	43.4322338	,	39.9387699	),
                LatLng(	43.4322262	,	39.9387859	),
                LatLng(	43.4321624	,	39.9387244	),
                LatLng(	43.4321231	,	39.9386894	),
                LatLng(	43.4310672	,	39.938954	),
                ).fillColor(ContextCompat.getColor(this, R.color.red))
    )



    /**
     * Prompts the user for permission to use the device location.
     */
    // [START maps_current_place_location_permission]
    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
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
    // [END maps_current_place_location_permission]

    /**
     * Handles the result of the request for location permissions.
     */
    // [START maps_current_place_on_request_permissions_result]
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        locationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    locationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }
    // [END maps_current_place_on_request_permissions_result]

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    // [START maps_current_place_show_current_place]
    @SuppressLint("MissingPermission")
    private fun showCurrentPlace() {
        if (map == null) {
            return
        }
        if (locationPermissionGranted) {
            // Use fields to define the data types to return.
            val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)

            // Use the builder to create a FindCurrentPlaceRequest.
            val request = FindCurrentPlaceRequest.newInstance(placeFields)

            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            val placeResult = placesClient.findCurrentPlace(request)
            placeResult.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val likelyPlaces = task.result

                    // Set the count, handling cases where less than 5 entries are returned.
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

                    // Show a dialog offering the user the list of likely places, and add a
                    // marker at the selected place.
                    openPlacesDialog()
                } else {
                    Log.e(TAG, "Exception: %s", task.exception)
                }
            }
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.")

            // Add a default marker, because the user hasn't selected a place.
            map?.addMarker(
                MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(defaultLocation)
                    .snippet(getString(R.string.default_info_snippet))
            )

            // Prompt the user for permission.
            getLocationPermission()
        }
    }
    // [END maps_current_place_show_current_place]

    /**
     * Displays a form allowing the user to select a place from a list of likely places.
     */
    // [START maps_current_place_open_places_dialog]
    private fun openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        val listener =
            DialogInterface.OnClickListener { dialog, which -> // The "which" argument contains the position of the selected item.
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

                // Add a marker for the selected place, with an info window
                // showing information about that place.
                map?.addMarker(
                    MarkerOptions()
                        .title(likelyPlaceNames[which])
                        .position(markerLatLng)
                        .snippet(markerSnippet)
                )

                // Position the map's camera at the location of the marker.
                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        markerLatLng,
                        DEFAULT_ZOOM.toFloat()
                    )
                )
                Toast.makeText(this, "message", Toast.LENGTH_SHORT).show()
            }

        // Display the dialog.
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_place)
            .setItems(likelyPlaceNames, listener)
            .show()
    }
    // [END maps_current_place_open_places_dialog]

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    // [START maps_current_place_update_location_ui]
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
    // [END maps_current_place_update_location_ui]

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 19
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

        // Keys for storing activity state.
        // [START maps_current_place_state_keys]
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"
        // [END maps_current_place_state_keys]

        // Used for selecting the current place.
        private const val M_MAX_ENTRIES = 5
    }


}
