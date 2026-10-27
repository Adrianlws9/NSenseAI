package com.example.nsense

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.nsense.data.local.NoiseDatabase
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : FragmentActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        loadMeasurements()
    }

    private fun loadMeasurements() {
        val database = NoiseDatabase.getDatabase(this)

        CoroutineScope(Dispatchers.IO).launch {
            val measurements = database.noiseDao().getAllMeasurements()

            withContext(Dispatchers.Main) {
                var lastPoint: LatLng? = null

                for (m in measurements) {
                    if (m.latitude == 0.0 && m.longitude == 0.0) continue // skip old data with no location

                    val point = LatLng(m.latitude, m.longitude)
                    val color = when (m.category) {
                        "Low" -> BitmapDescriptorFactory.HUE_GREEN
                        "Moderate" -> BitmapDescriptorFactory.HUE_YELLOW
                        else -> BitmapDescriptorFactory.HUE_RED
                    }

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(point)
                            .title("${m.noiseLevel.toInt()} dB — ${m.category}")
                            .icon(BitmapDescriptorFactory.defaultMarker(color))
                    )
                    lastPoint = point
                }

                lastPoint?.let {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 14f))
                }
            }
        }
    }
}