package com.example.nsense

import android.os.Bundle
import android.widget.Toast
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

    // If set, only readings of this sound source are shown
    private var sourceFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        sourceFilter = intent.getStringExtra("source")

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
            val measurements = database.noiseDao().getMeasurementsForUser(SessionManager.userId(this@MapActivity))

            withContext(Dispatchers.Main) {
                var lastPoint: LatLng? = null

                for (m in measurements) {
                    if (m.latitude == 0.0 && m.longitude == 0.0) continue // skip old data with no location
                    if (sourceFilter != null && m.noiseType != sourceFilter) continue

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
                            .snippet(m.noiseType)
                            .icon(BitmapDescriptorFactory.defaultMarker(color))
                    )
                    lastPoint = point
                }

                if (lastPoint != null) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 14f))
                } else {
                    Toast.makeText(
                        this@MapActivity,
                        "No saved readings with a location to show",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}