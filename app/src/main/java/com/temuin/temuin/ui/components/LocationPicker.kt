package com.temuin.temuin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.*
//import com.temuin.temuin.data.model.LocationData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPicker(
    onLocationSelected: (LocationData) -> Unit,
    onDismiss: () -> Unit,
    initialLocation: LocationData? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf(initialLocation) }
    var showSearchResults by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Camera position state for the map
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initialLocation?.let { LatLng(it.latitude, it.longitude) } 
                ?: LatLng(-6.2088, 106.8456), // Default to Jakarta
            15f
        )
    }
    
    // Current marker position
    var markerPosition by remember {
        mutableStateOf(
            initialLocation?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(-6.2088, 106.8456)
        )
    }
    
    // Search for places
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            delay(300) // Debounce search
            isLoading = true
            
            val token = AutocompleteSessionToken.newInstance()
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(token)
                .setQuery(searchQuery)
                .build()
                
            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    searchResults = response.autocompletePredictions
                    isLoading = false
                    showSearchResults = true
                }
                .addOnFailureListener {
                    searchResults = emptyList()
                    isLoading = false
                }
        } else {
            searchResults = emptyList()
            showSearchResults = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top App Bar
                TopAppBar(
                    title = { Text("Select Location") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (selectedLocation != null) {
                            TextButton(
                                onClick = { 
                                    selectedLocation?.let { onLocationSelected(it) }
                                }
                            ) {
                                Text("Done")
                            }
                        }
                    }
                )
                
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search for a location") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                showSearchResults = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true
                )
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // Google Map
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            markerPosition = latLng
                            
                            // Reverse geocoding to get address
                            reverseGeocode(latLng) { locationData ->
                                selectedLocation = locationData
                            }
                        }
                    ) {
                        selectedLocation?.let { location ->
                            Marker(
                                state = MarkerState(position = markerPosition),
                                title = "Selected Location",
                                snippet = location.address,
                                draggable = true,
                                onInfoWindowClick = {
                                    onLocationSelected(location)
                                }
                            )
                        }
                    }
                    
                    // Current Location Button
                    FloatingActionButton(
                        onClick = {
                            getCurrentLocation(context) { location ->
                                val latLng = LatLng(location.latitude, location.longitude)
                                markerPosition = latLng
                                selectedLocation = location
                                
                                // Move camera to current location
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = "My Location",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    // Search Results Overlay
                    if (showSearchResults && searchResults.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                items(searchResults) { prediction ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            // Fetch place details and move to location
                                            fetchPlaceDetails(
                                                placesClient = placesClient,
                                                placeId = prediction.placeId,
                                                onResult = { location ->
                                                    val latLng = LatLng(location.latitude, location.longitude)
                                                    markerPosition = latLng
                                                    selectedLocation = location
                                                    searchQuery = location.address
                                                    showSearchResults = false
                                                    
                                                    // Move camera to selected location
                                                    coroutineScope.launch {
                                                        cameraPositionState.animate(
                                                            CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Column {
                                                Text(
                                                    prediction.getPrimaryText(null).toString(),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    prediction.getSecondaryText(null).toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Selected Location Info Card
                    selectedLocation?.let { location ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            shadowElevation = 4.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Selected Location",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = location.address,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Button(
                                    onClick = { onLocationSelected(location) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Confirm Location")
                                }
                            }
                        }
                    }
                    
                    // Loading indicator
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Text("Searching...")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCurrentLocation(
    context: android.content.Context,
    onResult: (LocationData) -> Unit
) {
    // This is a simplified version - in a real app you'd use FusedLocationProviderClient
    // For now, we'll use a default location (Jakarta)
    val defaultLocation = LocationData(
        latitude = -6.2088,
        longitude = 106.8456,
        address = "Current Location"
    )
    onResult(defaultLocation)
}

private fun reverseGeocode(
    latLng: LatLng,
    onResult: (LocationData) -> Unit
) {
    // For reverse geocoding, we'll use a simplified approach
    // In a production app, you might want to use the Geocoding API
    val locationData = LocationData(
        latitude = latLng.latitude,
        longitude = latLng.longitude,
        address = "Selected Location (${String.format("%.6f", latLng.latitude)}, ${String.format("%.6f", latLng.longitude)})"
    )
    onResult(locationData)
}

private fun fetchPlaceDetails(
    placesClient: com.google.android.libraries.places.api.net.PlacesClient,
    placeId: String,
    onResult: (LocationData) -> Unit
) {
    val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
    val request = FetchPlaceRequest.newInstance(placeId, placeFields)
    
    placesClient.fetchPlace(request)
        .addOnSuccessListener { response ->
            val place = response.place
            val location = LocationData(
                latitude = place.latLng?.latitude ?: 0.0,
                longitude = place.latLng?.longitude ?: 0.0,
                address = place.address ?: place.name ?: "Unknown Location"
            )
            onResult(location)
        }
        .addOnFailureListener {
            // Handle error - use a default location
            val defaultLocation = LocationData(
                latitude = 0.0,
                longitude = 0.0,
                address = "Unknown Location"
            )
            onResult(defaultLocation)
        }
}
*/
