package com.climaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import com.climaai.app.data.api.RainViewerApi
import com.climaai.app.data.api.RainViewerFrame
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Base maps the radar is drawn over. Both are public and need no key.
 *
 * Temperature, Precipitation, Clouds and Wind used to sit next to these as selectable
 * layers. They were OpenWeatherMap tiles requested with appid=demo, which is not a key:
 * every tile came back 401 and the layer rendered nothing. This build carries no
 * OpenWeatherMap key to put in their place, so the entries are gone rather than left as
 * dead options — RainViewer's radar already covers precipitation.
 */
private enum class MapBaseLayer(
    val displayName: String,
    val icon: ImageVector,
    val tileUrl: String,
    val attribution: String
) {
    DARK(
        "Map",
        Icons.Default.Map,
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
        "&copy; OpenStreetMap, &copy; CARTO"
    ),
    SATELLITE(
        "Satellite",
        Icons.Default.Satellite,
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "Imagery &copy; Esri"
    )
}

private sealed interface RadarFrames {
    object Loading : RadarFrames

    data class Ready(
        /** Tile host from the index; not hard-coded, RainViewer may move it. */
        val host: String,
        val frames: List<RainViewerFrame>,
        /** Frames before this index are observations, the rest are nowcast. */
        val observedCount: Int
    ) : RadarFrames

    data class Failed(val message: String) : RadarFrames
}

/**
 * Zoom range RainViewer's free tile service actually serves.
 *
 * Above 7 it answers 200 with a placeholder PNG reading "Zoom Level Not
 * Supported" — byte-identical at z8 and z9, which is how you can tell it apart
 * from real radar. Nothing errors, so the map simply fills with that text
 * tiled across it. The zoom control used to allow up to 15 and opened at 8, so
 * the radar was unusable at its default zoom and every zoom above it.
 */
private const val RADAR_MIN_ZOOM = 3
private const val RADAR_MAX_ZOOM = 7

/**
 * Origin the radar page is loaded under.
 *
 * Any real https origin works; what matters is that it is not null. Using the
 * CDN the page's script comes from also makes that load same-origin rather
 * than cross-origin.
 */
private const val LEAFLET_ORIGIN = "https://unpkg.com/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarMapScreen(
    latitude: Double,
    longitude: Double,
    onBack: () -> Unit
) {
    val api = remember { RainViewerApi.create() }
    val clock = remember { DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) }

    var baseLayer by remember { mutableStateOf(MapBaseLayer.DARK) }
    var framesState by remember { mutableStateOf<RadarFrames>(RadarFrames.Loading) }
    var reloadToken by remember { mutableStateOf(0) }
    var frameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(RADAR_MAX_ZOOM) }

    LaunchedEffect(reloadToken) {
        framesState = RadarFrames.Loading
        val loaded = loadRadarFrames(api)
        framesState = loaded
        // Open on the most recent observation rather than two hours ago.
        frameIndex = if (loaded is RadarFrames.Ready) (loaded.observedCount - 1).coerceAtLeast(0) else 0
        if (loaded !is RadarFrames.Ready) isPlaying = false
    }

    val ready = framesState as? RadarFrames.Ready
    val frames = ready?.frames ?: emptyList()

    LaunchedEffect(isPlaying, frames.size) {
        if (!isPlaying || frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(500)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Radar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadToken++ }) {
                        Icon(Icons.Default.Refresh, "Reload radar frames")
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
                // Base map chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapBaseLayer.values().forEach { layer ->
                        FilterChip(
                            selected = baseLayer == layer,
                            onClick = { baseLayer = layer },
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
                        .background(Color(0xFF0F0F1A))
                ) {
                    RadarWebMap(
                        latitude = latitude,
                        longitude = longitude,
                        zoom = zoom,
                        baseLayer = baseLayer,
                        host = ready?.host ?: "",
                        frames = frames,
                        frameIndex = frameIndex,
                        framesResolved = framesState !is RadarFrames.Loading
                    )

                    RadarStatusBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        state = framesState,
                        frameIndex = frameIndex,
                        clock = clock,
                        onRetry = { reloadToken++ }
                    )

                    // Zoom controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { if (zoom < RADAR_MAX_ZOOM) zoom++ },
                            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.9f),
                            contentColor = Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Add, "Zoom in", Modifier.size(20.dp))
                        }
                        FloatingActionButton(
                            onClick = { if (zoom > RADAR_MIN_ZOOM) zoom-- },
                            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.9f),
                            contentColor = Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Zoom out", Modifier.size(20.dp))
                        }
                    }
                }

                // Playback only means something once there is more than one real frame.
                if (ready != null && frames.size > 1) {
                    PlaybackControls(
                        ready = ready,
                        frameIndex = frameIndex,
                        isPlaying = isPlaying,
                        clock = clock,
                        onFrameChange = { frameIndex = it },
                        onPlayToggle = { isPlaying = !isPlaying }
                    )
                }

                Text(
                    "Radar from RainViewer, coloured on its Universal Blue scale.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RadarStatusBadge(
    modifier: Modifier,
    state: RadarFrames,
    frameIndex: Int,
    clock: DateFormat,
    onRetry: () -> Unit
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state) {
            is RadarFrames.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = Color(0xFF667EEA),
                    strokeWidth = 2.dp
                )
                Text(
                    "Loading radar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            is RadarFrames.Failed -> {
                Icon(
                    Icons.Default.CloudOff,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFFF8A80)
                )
                Text(
                    "No radar: ${state.message}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Retry",
                    color = Color(0xFF667EEA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onRetry)
                )
            }

            is RadarFrames.Ready -> {
                val frame = state.frames.getOrNull(frameIndex)
                Icon(
                    Icons.Default.Radar,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF667EEA)
                )
                Text(
                    if (frame != null) clock.format(Date(frame.time * 1000L)) else "Radar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    ready: RadarFrames.Ready,
    frameIndex: Int,
    isPlaying: Boolean,
    clock: DateFormat,
    onFrameChange: (Int) -> Unit,
    onPlayToggle: () -> Unit
) {
    val frames = ready.frames
    val isForecast = frameIndex >= ready.observedCount

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
            // Real frame times: the span is whatever RainViewer published, which is not
            // always the -2h/+2h the old hardcoded labels claimed.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    clock.format(Date(frames.first().time * 1000L)),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    buildString {
                        append(clock.format(Date(frames[frameIndex.coerceIn(frames.indices)].time * 1000L)))
                        append(if (isForecast) " · Forecast" else " · Observed")
                    },
                    color = Color(0xFF667EEA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    clock.format(Date(frames.last().time * 1000L)),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            Slider(
                value = frameIndex.toFloat(),
                onValueChange = { onFrameChange(it.toInt().coerceIn(frames.indices)) },
                valueRange = 0f..frames.lastIndex.toFloat(),
                steps = (frames.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF667EEA),
                    activeTrackColor = Color(0xFF667EEA)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (frameIndex > 0) onFrameChange(frameIndex - 1) }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White)
                }

                FloatingActionButton(
                    onClick = onPlayToggle,
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

                IconButton(onClick = { if (frameIndex < frames.lastIndex) onFrameChange(frameIndex + 1) }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                }
            }
        }
    }
}

private suspend fun loadRadarFrames(api: RainViewerApi): RadarFrames {
    return try {
        val response = api.getRadarFrames()
        val body = response.body()

        when {
            !response.isSuccessful || body == null ->
                RadarFrames.Failed("RainViewer returned ${response.code()}")

            body.radar.past.isEmpty() && body.radar.nowcast.isNullOrEmpty() ->
                RadarFrames.Failed("no frames published")

            else -> RadarFrames.Ready(
                host = body.host,
                // Nowcast is regularly absent from the public feed, so it is appended only
                // when it is actually there instead of being assumed.
                frames = body.radar.past + (body.radar.nowcast ?: emptyList()),
                observedCount = body.radar.past.size
            )
        }
    } catch (e: Exception) {
        RadarFrames.Failed(e.message ?: "could not reach RainViewer")
    }
}

/**
 * Records the document the WebView is already showing. Deliberately a plain holder rather
 * than snapshot state — it is written during the update pass and must not feed back into
 * recomposition.
 */
private class LoadedDocument {
    var html: String? = null
}

@Composable
private fun RadarWebMap(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    baseLayer: MapBaseLayer,
    host: String,
    frames: List<RainViewerFrame>,
    frameIndex: Int,
    framesResolved: Boolean
) {
    // Deliberately not keyed on frameIndex or zoom. Rebuilding the document to step a frame
    // means refetching Leaflet and re-creating the map twice a second, which is why the
    // previous version could never animate; both are pushed into the live page instead.
    // Null until the frame list resolves, so the page is built once with its overlays
    // already in it rather than loaded empty and immediately reloaded.
    val html = remember(latitude, longitude, baseLayer, host, frames, framesResolved) {
        if (framesResolved) buildMapHtml(latitude, longitude, zoom, frameIndex, baseLayer, host, frames) else null
    }
    val loaded = remember { LoadedDocument() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // AndroidView gives a child WRAP_CONTENT by default. A WebView
                    // sized that way reports a zero-height content viewport, so
                    // every CSS height that resolves against the viewport — 100%,
                    // 100vh — collapses to 0. The view still paints at its Compose
                    // size, which is why the map looked present but empty.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            Log.w(
                                "RadarMap",
                                "resource failed: ${request?.url} -> ${error?.description}"
                            )
                        }
                    }
                    // A failure inside the WebView is otherwise completely
                    // silent — the map just renders blank, with nothing in
                    // logcat. That is how the Leaflet load below stayed broken.
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.d(
                                "RadarMap",
                                "${message.messageLevel()}: ${message.message()} " +
                                    "(line ${message.lineNumber()})"
                            )
                            return true
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // No useWideViewPort/loadWithOverviewMode. Those exist to fit
                    // desktop-width pages onto a phone: they lay the page out
                    // against a wide virtual viewport and then zoom it to fit.
                    // This page is not a desktop page, it is a map that must fill
                    // the view exactly, and that virtual viewport is what left the
                    // map container 0px tall.
                }
            },
            update = { webView ->
                when {
                    html == null -> Unit
                    loaded.html != html -> {
                        loaded.html = html
                        // The base URL must be a real https origin, not null.
                        // With null, WebView loads the markup as a data: URL,
                        // and Chromium refuses to load external subresources
                        // from a data: origin — so the Leaflet <script> tag was
                        // silently blocked, L was never defined, and the map
                        // rendered as an empty white box. Nothing appeared in
                        // logcat, and the frame scrubber still worked because it
                        // is native Compose, which made it look like a styling
                        // bug rather than a page that never ran.
                        webView.loadDataWithBaseURL(
                            LEAFLET_ORIGIN, html, "text/html", "UTF-8", null
                        )
                    }
                    else -> webView.evaluateJavascript("showFrame($frameIndex); setZoom($zoom);", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun buildMapHtml(
    latitude: Double,
    longitude: Double,
    initialZoom: Int,
    initialFrame: Int,
    baseLayer: MapBaseLayer,
    host: String,
    frames: List<RainViewerFrame>
): String {
    // Every URL comes from the frame's own path. Interpolating a frame index or a timestamp
    // into the tile path is what made every radar tile 404 before.
    val frameUrls = frames.joinToString(", ") { "'${it.tileUrlTemplate(host)}'" }

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            /*
               The height chain matters. #map was height:100vh, which resolved
               to 0 in the WebView: with no viewport meta tag it lays out against
               a default 980px-wide viewport whose height is not the view's, so
               vh had nothing to resolve against. Leaflet loaded and even built
               tiles into a container 380px wide and 0px tall, which renders as a
               blank white box and reports no error anywhere.
            */
            html, body { margin: 0; padding: 0; height: 100%; }
            #map { width: 100%; height: 100%; background: #0F0F1A; }
            .leaflet-control-attribution {
                font-size: 9px;
                background: rgba(0, 0, 0, 0.45);
                color: #C7CBE0;
            }
            .leaflet-control-attribution a { color: #9AA4D6; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', { zoomControl: false }).setView([$latitude, $longitude], $initialZoom);

            L.tileLayer('${baseLayer.tileUrl}', {
                attribution: '${baseLayer.attribution}'
            }).addTo(map);

            var radarUrls = [$frameUrls];
            var radarLayers = new Array(radarUrls.length);
            var shownFrame = -1;

            function layerFor(index) {
                if (!radarLayers[index]) {
                    radarLayers[index] = L.tileLayer(radarUrls[index], {
                        opacity: 0,
                        zIndex: 10,
                        attribution: 'Radar &copy; RainViewer'
                    }).addTo(map);
                }
                return radarLayers[index];
            }

            function showFrame(index) {
                if (index < 0 || index >= radarUrls.length) { return; }
                var next = layerFor(index);
                if (shownFrame >= 0 && shownFrame !== index) {
                    radarLayers[shownFrame].setOpacity(0);
                }
                next.setOpacity(0.75);
                shownFrame = index;
                // Warm the following frame so playback does not stall on a cold tile fetch.
                if (index + 1 < radarUrls.length) { layerFor(index + 1); }
            }

            function setZoom(level) {
                if (map.getZoom() !== level) { map.setZoom(level); }
            }

            L.circleMarker([$latitude, $longitude], {
                radius: 5,
                color: '#FFFFFF',
                weight: 2,
                fillColor: '#667EEA',
                fillOpacity: 1
            }).addTo(map);

            showFrame($initialFrame);
        </script>
    </body>
    </html>
    """.trimIndent()
}
