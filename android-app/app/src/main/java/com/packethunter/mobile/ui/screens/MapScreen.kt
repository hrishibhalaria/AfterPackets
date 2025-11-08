package com.packethunter.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.packethunter.mobile.data.IpTalker
import com.packethunter.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(talkers: List<IpTalker>) {
    // Google Pixel 7 screen: 1080 x 2400 pixels, 6.3 inch display
    // Map height optimized for phone viewing
    val mapHeight = 500.dp // Optimized for Pixel 7 screen
    
    // Default center position (San Francisco)
    val defaultPosition = LatLng(37.7749, -122.4194)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 2f)
    }
    
    // Filter talkers with valid coordinates or add sample data
    val validTalkers = if (talkers.any { it.lat != null && it.lon != null }) {
        talkers.filter { it.lat != null && it.lon != null }
    } else {
        // Add sample coordinates for demo
        talkers.take(5).mapIndexed { idx, talker ->
            talker.copy(
                lat = 20.0 + (idx * 10) - 20.0,
                lon = -20.0 + (idx * 20),
                country = talker.country ?: "Unknown"
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "🗺️ Geo-Location Map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen
                )
                Text(
                    "Remote hosts plotted by location • ${validTalkers.size} hosts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            }
        }
        
        // Google Maps view - Optimized for Pixel 7
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight),
            colors = CardDefaults.cardColors(containerColor = SurfaceGrayDark)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = MapType.NORMAL,
                    isMyLocationEnabled = false
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = false
                )
            ) {
                // User location marker
                Marker(
                    state = MarkerState(position = defaultPosition),
                    title = "Your Device",
                    snippet = "Capture Source"
                )
                
                // Remote host markers
                validTalkers.forEach { talker ->
                    talker.lat?.let { lat ->
                        talker.lon?.let { lon ->
                            val position = LatLng(lat, lon)
                            
                            Marker(
                                state = MarkerState(position = position),
                                title = talker.ip,
                                snippet = "${talker.country ?: "Unknown"} • ${talker.packetCount} packets"
                            )
                            
                            // Draw line from user to remote host
                            Polyline(
                                points = listOf(defaultPosition, position),
                                color = Color.Red,
                                width = 5f
                            )
                        }
                    }
                }
            }
        }
        
        // Location list
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📍 Remote Hosts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                validTalkers.take(5).forEach { talker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(talker.ip, color = CyberCyan, style = MaterialTheme.typography.bodyMedium)
                            talker.country?.let {
                                Text(
                                    "$it (${talker.lat}, ${talker.lon})",
                                    color = TextGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            "${talker.packetCount} pkts",
                            color = TextGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (talker != validTalkers.last()) {
                        Divider(color = BorderGray)
                    }
                }
            }
        }
    }
}
