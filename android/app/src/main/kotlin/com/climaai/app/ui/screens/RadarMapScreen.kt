package com.climaai.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay

/**
 * Map layer types - includes Satellite for AccuWeather parity
 */
enum class MapLayer(
    val displayName: String, 
    val icon: ImageVector,
    val tileUrl: String,
    val isAnimated: Boolean = true
) {
    RADAR("Radar", Icons.Default.Radar, 
        "https://tilecache.rainviewer.com/v2/radar/{ts}/256/{z}/{x}/{y}/2/1_1.png", true),
    SATELLITE("Satellite", Icons.Default.Satellite, 
        "https://tiles.openweathermap.org/map/clouds_new/{z}/{x}/{y}.png?appid=demo", false),
    TEMPERATURE("Temperature", Icons.Default.Thermostat, 
        "https://tiles.openweathermap.org/map/temp_new/{z}/{x}/{y}.png?appid=demo", false),
    PRECIPITATION("Precipitation", Icons.Default.WaterDrop, 
        "https://tiles.openweathermap.org/map/precipitation_new/{z}/{x}/{y}.png?appid=demo", true),
    CLOUDS("Clouds", Icons.Default.Cloud, 
        "https://tiles.openweathermap.org/map/clouds_new/{z}/{x}/{y}.png?appid=demo", false),
    WIND("Wind", Icons.Default.Air, 
        "https://tiles.openweathermap.org/map/wind_new/{z}/{x}/{y}.png?appid=demo", false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarMapScreen(
    latitude: Double,
    longitude: Double,
    onBack: () -> Unit
) {
    var selectedLayer by remember { mutableStateOf(MapLayer.RADAR) }
    var isPlaying by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(8) }
    var currentFrame by remember { mutableStateOf(0) }
    var showLayerPicker by remember { mutableStateOf(false) }
    
    // Animation loop for radar
    LaunchedEffect(isPlaying, selectedLayer) {
        if (isPlaying && selectedLayer.isAnimated) {
            while (isPlaying) {
                delay(500)
                currentFrame = (currentFrame + 1) % 10
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (selectedLayer) {
                            MapLayer.RADAR -> "Weather Radar"
                            MapLayer.SATELLITE -> "Satellite Imagery"
                            MapLayer.TEMPERATURE -> "Temperature Map"
                            else -> "${selectedLayer.displayName} Map"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLayerPicker = !showLayerPicker }) {
                        Icon(Icons.Default.Layers, "Layers")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Quick layer chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(MapLayer.RADAR, MapLayer.SATELLITE, MapLayer.TEMPERATURE).forEach { layer ->
                        FilterChip(
                            selected = selectedLayer == layer,
                            onClick = { selectedLayer = layer },
                            label = { Text(layer.displayName, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    layer.icon, 
                                    null, 
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF667EEA),
                                selectedLabelColor = Color.White,
                                labelColor = Color.White.copy(alpha = 0.7f)
                            )
                        )
                    }
                }

                // Map view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    EnhancedMapView(
                        latitude = latitude,
                        longitude = longitude,
                        zoom = zoom,
                        layer = selectedLayer,
                        frame = currentFrame
                    )

                    // Layer indicator badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                selectedLayer.icon,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF667EEA)
                            )
                            Text(
                                selectedLayer.displayName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Zoom controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { if (zoom < 15) zoom++ },
                            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.9f),
                            contentColor = Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Add, "Zoom in", Modifier.size(20.dp))
                        }
                        FloatingActionButton(
                            onClick = { if (zoom > 3) zoom-- },
                            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.9f),
                            contentColor = Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Zoom out", Modifier.size(20.dp))
                        }
                    }
                }

                // Playback controls (only for animated layers)
                AnimatedVisibility(
                    visible = selectedLayer.isAnimated,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Timeline slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("-2h", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text("Now", color = Color(0xFF667EEA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("+2h", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            
                            Slider(
                                value = currentFrame.toFloat(),
                                onValueChange = { currentFrame = it.toInt() },
                                valueRange = 0f..9f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF667EEA),
                                    activeTrackColor = Color(0xFF667EEA)
                                )
                            )
                            
                            // Playback buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { if (currentFrame > 0) currentFrame-- }) {
                                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White)
                                }
                                
                                FloatingActionButton(
                                    onClick = { isPlaying = !isPlaying },
                                    containerColor = Color(0xFF667EEA),
                                    contentColor = Color.White,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        if (isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                IconButton(onClick = { if (currentFrame < 9) currentFrame++ }) {
                                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Legend
                MapLegend(layer = selectedLayer)

                Spacer(Modifier.height(16.dp))
            }
            
            // Layer picker overlay
            AnimatedVisibility(
                visible = showLayerPicker,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                LayerPickerSheet(
                    selectedLayer = selectedLayer,
                    onLayerSelected = { 
                        selectedLayer = it
                        showLayerPicker = false
                    },
                    onDismiss = { showLayerPicker = false }
                )
            }
        }
    }
}

@Composable
private fun LayerPickerSheet(
    selectedLayer: MapLayer,
    onLayerSelected: (MapLayer) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(top = 70.dp, end = 16.dp)
            .width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Map Layers",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp)
            )
            
            MapLayer.values().forEach { layer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLayerSelected(layer) }
                        .background(
                            if (selectedLayer == layer) Color(0xFF667EEA).copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        layer.icon,
                        null,
                        tint = if (selectedLayer == layer) Color(0xFF667EEA) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        layer.displayName,
                        color = if (selectedLayer == layer) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (selectedLayer == layer) FontWeight.Medium else FontWeight.Normal
                    )
                    if (selectedLayer == layer) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color(0xFF667EEA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedMapView(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    layer: MapLayer,
    frame: Int
) {
    // Build map URL with layer overlay
    val mapHtml = remember(latitude, longitude, zoom, layer, frame) {
        buildMapHtml(latitude, longitude, zoom, layer, frame)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    null,
                    mapHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun buildMapHtml(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    layer: MapLayer,
    frame: Int
): String {
    // Using Leaflet.js for better map rendering with tile overlays
    val tileLayer = when (layer) {
        MapLayer.RADAR -> "https://tilecache.rainviewer.com/v2/radar/nowcast_$frame/256/{z}/{x}/{y}/2/1_1.png"
        MapLayer.SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        MapLayer.TEMPERATURE -> "https://tile.openweathermap.org/map/temp_new/{z}/{x}/{y}.png?appid=demo"
        MapLayer.PRECIPITATION -> "https://tile.openweathermap.org/map/precipitation_new/{z}/{x}/{y}.png?appid=demo"
        MapLayer.CLOUDS -> "https://tile.openweathermap.org/map/clouds_new/{z}/{x}/{y}.png?appid=demo"
        MapLayer.WIND -> "https://tile.openweathermap.org/map/wind_new/{z}/{x}/{y}.png?appid=demo"
    }
    
    val baseLayer = if (layer == MapLayer.SATELLITE) {
        "" // Satellite is the base
    } else {
        """L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png').addTo(map);"""
    }
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            body { margin: 0; padding: 0; }
            #map { width: 100%; height: 100vh; background: #0F0F1A; }
            .leaflet-control-attribution { display: none; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', {
                zoomControl: false,
                attributionControl: false
            }).setView([$latitude, $longitude], $zoom);
            
            $baseLayer
            
            L.tileLayer('$tileLayer', {
                opacity: ${if (layer == MapLayer.SATELLITE) 1.0 else 0.7}
            }).addTo(map);
            
            L.marker([$latitude, $longitude]).addTo(map);
        </script>
    </body>
    </html>
    """.trimIndent()
}

@Composable
private fun MapLegend(layer: MapLayer) {
    val legendItems = when (layer) {
        MapLayer.RADAR, MapLayer.PRECIPITATION -> listOf(
            Pair(Color(0xFF00FF00), "Light"),
            Pair(Color(0xFFFFFF00), "Moderate"),
            Pair(Color(0xFFFF8000), "Heavy"),
            Pair(Color(0xFFFF0000), "Intense")
        )
        MapLayer.SATELLITE -> listOf(
            Pair(Color(0xFF4A90D9), "Water"),
            Pair(Color(0xFF2E7D32), "Vegetation"),
            Pair(Color(0xFF8D6E63), "Urban"),
            Pair(Color(0xFFFFFFFF), "Clouds/Snow")
        )
        MapLayer.TEMPERATURE -> listOf(
            Pair(Color(0xFF0000FF), "<32°F"),
            Pair(Color(0xFF00FFFF), "32-50°F"),
            Pair(Color(0xFF00FF00), "50-70°F"),
            Pair(Color(0xFFFFFF00), "70-85°F"),
            Pair(Color(0xFFFF0000), ">85°F")
        )
        MapLayer.CLOUDS -> listOf(
            Pair(Color.White.copy(alpha = 0.3f), "Light"),
            Pair(Color(0xFFCCCCCC), "Moderate"),
            Pair(Color(0xFF888888), "Dense")
        )
        MapLayer.WIND -> listOf(
            Pair(Color(0xFF00BFFF), "<10 mph"),
            Pair(Color(0xFF00FF00), "10-25 mph"),
            Pair(Color(0xFFFFFF00), "25-40 mph"),
            Pair(Color(0xFFFF0000), ">40 mph")
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        legendItems.forEach { (color, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
