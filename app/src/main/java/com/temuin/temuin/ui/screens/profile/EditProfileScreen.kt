package com.temuin.temuin.ui.screens.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.temuin.temuin.R
import com.temuin.temuin.ui.screens.auth.PhoneAuthViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onViewFullPhoto: (Bitmap) -> Unit,
    onNavigateToStatus: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmapImage by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val firebaseAuth = Firebase.auth
    val userId = firebaseAuth.currentUser?.uid ?: ""
    val phoneNumber = firebaseAuth.currentUser?.phoneNumber ?: ""

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load current user data
    LaunchedEffect(Unit) {
        viewModel.getCurrentUserProfile(userId) { user ->
            name = user.name
            tempName = user.name
            status = user.status
            // Convert base64 to bitmap if profile image exists
            user.profileImage?.let { base64Image ->
                try {
                    val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
                    val inputStream: InputStream = ByteArrayInputStream(imageBytes)
                    bitmapImage = BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateProfileImage(bitmap: Bitmap?) {
        val userId = viewModel.getCurrentUserId()
        if (userId != null) {
            scope.launch {
                try {
                    val base64Image = bitmap?.let {
                        viewModel.convertBitmapToBase64(bitmap)
                    }

                    viewModel.updateProfileImage(userId, base64Image)
                    bitmapImage = bitmap
                } catch (e: Exception) {
                    Log.e("EditProfileScreen", "Error updating profile image", e)
                    snackbarHostState.showSnackbar("Error: Failed to update profile image")
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profileImageUri = uri
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
                updateProfileImage(bitmap)
            } catch (e: Exception) {
                Log.e("EditProfileScreen", "Error loading image", e)
                scope.launch {
                    snackbarHostState.showSnackbar("Error: Failed to load image")
                }
            }
        }
    }

    // Name Edit Dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Enter your name") },
                    supportingText = if (tempName.isBlank()) {
                        { Text("Name is required") }
                    } else null,
                    isError = tempName.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            scope.launch {
                                try {
                                    viewModel.saveUserProfile(
                                        userId = userId,
                                        name = tempName,
                                        status = status,
                                        profileImage = bitmapImage
                                    )
                                    name = tempName
                                    showNameDialog = false
                                } catch (e: Exception) {
                                    Log.e("EditProfileScreen", "Error updating name", e)
                                    snackbarHostState.showSnackbar("Error: Failed to update name")
                                }
                            }
                        }
                    },
                    enabled = tempName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showNameDialog = false
                    tempName = name
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Photo Options Dialog
    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                            showPhotoOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change profile photo")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    viewModel.updateProfileImage(userId, null)
                                    bitmapImage = null
                                    showPhotoOptions = false
                                } catch (e: Exception) {
                                    Log.e("EditProfileScreen", "Error removing profile image", e)
                                    snackbarHostState.showSnackbar("Error: Failed to remove profile image")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Remove profile photo",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            properties = DialogProperties(dismissOnClickOutside = true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Profile Image Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                // Profile Image
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            enabled = bitmapImage != null,
                            onClick = { bitmapImage?.let { onViewFullPhoto(it) } }
                        )
                ) {
                    if (bitmapImage != null) {
                        Image(
                            bitmap = bitmapImage!!.asImageBitmap(),
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Add Photo",
                            modifier = Modifier.fillMaxSize(),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Edit Button
                TextButton(
                    onClick = { 
                        if (bitmapImage != null) {
                            showPhotoOptions = true
                        } else {
                            imagePickerLauncher.launch("image/*")
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = if (bitmapImage != null) "Edit" else "Add",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Profile Information
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNameDialog = true }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                    Text(
                            text = "Name",
                            style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Status Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToStatus() }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                    Text(
                            text = "About",
                            style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Phone Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Your phone number",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = phoneNumber,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
} 