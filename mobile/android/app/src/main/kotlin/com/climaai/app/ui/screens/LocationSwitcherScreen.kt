package com.climaai.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Saved location data
 */
data class SavedLocation(
    val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrent: Boolean = false
)

/**
 * Search result from geocoding
 */
data class LocationSearchResult(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSwitcherScreen(
    currentLocation: SavedLocation?,
    savedLocations: List<SavedLocation>,
    onLocationSelected: (SavedLocation) -> Unit,
    onAddLocation: (LocationSearchResult) -> Unit,
    onDeleteLocation: (SavedLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LocationSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Real geocoding with Nominatim API
    fun performSearch(query: String) {
        if (query.length < 2) {
            searchResults = emptyList()
            return
        }
        
        isSearching = true
        coroutineScope.launch {
            delay(500) // Debounce - Nominatim rate limit is 1/sec
            
            try {
                val api = com.climaai.app.data.api.NominatimApi.create()
                val response = api.search(query)
                
                if (response.isSuccessful && response.body() != null) {
                    searchResults = response.body()!!.mapNotNull { result ->
                        val address = result.address
                        val name = address?.getLocationName() ?: result.name ?: return@mapNotNull null
                        LocationSearchResult(
                            name = name,
                            region = address?.state ?: address?.county ?: "",
                            country = address?.country ?: "",
                            latitude = result.lat.toDoubleOrNull() ?: 0.0,
                            longitude = result.lon.toDoubleOrNull() ?: 0.0
                        )
                    }.distinctBy { "${it.name}, ${it.country}" }
                } else {
                    searchResults = emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("LocationSearch", "Nominatim error", e)
                searchResults = emptyList()
            }
            
            isSearching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(Modifier.size(32.dp, 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Text(
                "Locations",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    performSearch(it)
                },
                placeholder = { Text("Search city or country") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = ""
                            searchResults = emptyList()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF667EEA),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF667EEA),
                    focusedLeadingIconColor = Color(0xFF667EEA),
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.6f),
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                // Current location button
                item {
                    LocationRow(
                        icon = Icons.Default.MyLocation,
                        iconColor = Color(0xFF667EEA),
                        title = "Use Current Location",
                        subtitle = "GPS location",
                        onClick = {
                            onUseCurrentLocation()
                            onDismiss()
                        }
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(8.dp))
                }

                // Search results
                if (searchQuery.isNotEmpty()) {
                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF667EEA),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    } else if (searchResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.White.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No locations found",
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "Search Results",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        items(searchResults) { result ->
                            LocationRow(
                                icon = Icons.Default.Place,
                                iconColor = Color(0xFF38EF7D),
                                title = result.name,
                                subtitle = "${result.region}, ${result.country}",
                                trailing = {
                                    IconButton(onClick = {
                                        onAddLocation(result)
                                    }) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = Color(0xFF667EEA)
                                        )
                                    }
                                },
                                onClick = {
                                    onAddLocation(result)
                                    onDismiss()
                                }
                            )
                        }
                    }
                } else {
                    // Saved locations
                    if (savedLocations.isNotEmpty()) {
                        item {
                            Text(
                                "Saved Locations",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        items(savedLocations) { location ->
                            LocationRow(
                                icon = if (location.isCurrent) Icons.Default.MyLocation else Icons.Default.Place,
                                iconColor = if (location.isCurrent) Color(0xFF667EEA) else Color(0xFFFC466B),
                                title = location.name,
                                subtitle = location.country,
                                isSelected = currentLocation?.id == location.id,
                                trailing = {
                                    if (!location.isCurrent) {
                                        IconButton(onClick = { onDeleteLocation(location) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onLocationSelected(location)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                // Bottom padding
                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    isSelected: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) Color(0xFF667EEA).copy(alpha = 0.2f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Trailing content or checkmark
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF667EEA),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                trailing?.invoke()
            }
        }
    }
}
