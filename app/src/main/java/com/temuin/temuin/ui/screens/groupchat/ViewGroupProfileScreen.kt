package com.temuin.temuin.ui.screens.groupchat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.data.model.User
import com.temuin.temuin.R
import com.temuin.temuin.data.model.GroupMember
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewGroupProfileScreen(
    groupId: String,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    onMessageMember: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToChats: () -> Unit = {},
    onViewFullPhoto: (Bitmap) -> Unit,
    viewModel: ViewGroupProfileViewModel = hiltViewModel()
) {
    val groupInfo by viewModel.groupInfo.collectAsState()
    val members by viewModel.members.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showRemoveMemberDialog by remember { mutableStateOf<GroupMember?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    val currentUserId = viewModel.getCurrentUserId()

    // Make admin check reactive to groupInfo changes
    val isAdmin = remember(groupInfo?.createdBy, currentUserId) {
        val adminStatus = groupInfo?.createdBy == currentUserId
        Log.d("ViewGroupProfile", "Admin check: createdBy=${groupInfo?.createdBy}, currentUserId=$currentUserId, isAdmin=$adminStatus")
        adminStatus
    }
    val context = LocalContext.current
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (bytes != null) {
                    val base64String = Base64.encodeToString(bytes, Base64.DEFAULT)
                    viewModel.updateGroupProfileImage(groupId, base64String)
                }
            } catch (e: Exception) {
                Log.e("GroupProfile", "Error processing image: ${e.message}")
            }
        }
    }

    LaunchedEffect(groupId) {
        viewModel.loadGroupProfile(groupId)
    }

    // Update profileBitmap when groupInfo changes
    LaunchedEffect(groupInfo?.profileImage) {
        profileBitmap = groupInfo?.profileImage?.let { base64Image ->
            try {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val inputStream: InputStream = ByteArrayInputStream(imageBytes)
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Combined Edit Options Dialog
    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Edit group name - Always shown
                    TextButton(
                        onClick = {
                            newGroupName = groupInfo?.name ?: ""
                            showEditNameDialog = true
                            showPhotoOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Edit group name")
                    }

                    // Add/Change group photo
                    TextButton(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                            showPhotoOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (profileBitmap != null) "Edit group photo" else "Add group photo")
                    }

                    // Remove photo - Only shown if photo exists
                    if (profileBitmap != null) {
                        TextButton(
                            onClick = {
                                viewModel.updateGroupProfileImage(groupId, null)
                                showPhotoOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Remove group photo",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            properties = DialogProperties(dismissOnClickOutside = true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = TopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Add Member Button
                    OutlinedButton(
                        onClick = { showAddMemberDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_group_new_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Add Member")
                        }
                    }

                    /*OutlinedButton(
                        onClick = { showExitConfirmation = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Exit Group",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }*/
                    
                    // Exit Group Button
                    Button(
                        onClick = { showExitConfirmation = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Exit Group")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    // Group Profile Section
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Group Profile Picture
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(
                                        enabled = profileBitmap != null,
                                        onClick = { profileBitmap?.let { onViewFullPhoto(it) } }
                                    )
                            ) {
                                if (profileBitmap != null) {
                                    Image(
                                        bitmap = profileBitmap!!.asImageBitmap(),
                                        contentDescription = "Group profile picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_groups_24),
                                        contentDescription = "Default group icon",
                                        modifier = Modifier.fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Group Name
                            Text(
                                text = groupInfo?.name ?: "Unknown Group",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Created Info
                            Text(
                                text = "Created by ${
                                    if (groupInfo?.createdBy == currentUserId) "You" else (groupInfo?.creatorName ?: "Unknown")
                                } • ${
                                    groupInfo?.createdAt?.let { timestamp ->
                                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
                                    } ?: "Unknown date"
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isAdmin) {
                                TextButton(
                                    onClick = { showPhotoOptions = true }
                                ) {
                                    Text(
                                        text = "Edit Group",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Members Section
                    item {
                        Text(
                            text = "${members.size} Members",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(members) { member ->
                        GroupMemberItem(
                            member = member,
                            isAdmin = isAdmin,
                            currentUserId = currentUserId ?: "",
                            onMessageMember = onMessageMember,
                            onViewProfile = { onMemberClick(member.userId) },
                            onRemoveMember = { showRemoveMemberDialog = member },
                            onMakeAdmin = { 
                                viewModel.transferAdminRights(groupId, member.userId)
                            }
                        )
                    }
                }
            }

            // Add Member Dialog
            if (showAddMemberDialog) {
                AddMemberDialog(
                    friends = friends,
                    onDismiss = { showAddMemberDialog = false },
                    onAddMember = { selectedFriends ->
                        selectedFriends.forEach { friend ->
                            viewModel.addMemberToGroup(groupId, friend.userId)
                        }
                        showAddMemberDialog = false
                    }
                )
            }

            // Exit Group Confirmation
            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    title = { Text("Exit Group") },
                    text = { 
                        Text("Are you sure you want to exit this group? You will no longer receive messages from this group.") 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.leaveGroup(groupId) {
                                    showExitConfirmation = false
                                    onNavigateToChats() // Navigate to chat list after leaving
                                }
                            }
                        ) {
                            Text(
                                "Exit",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Remove Member Confirmation
            showRemoveMemberDialog?.let { member ->
                AlertDialog(
                    onDismissRequest = { showRemoveMemberDialog = null },
                    title = { Text("Remove Member") },
                    text = { 
                        Text("Are you sure you want to remove ${member.name} from this group?") 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.removeMemberFromGroup(groupId, member.userId)
                                showRemoveMemberDialog = null
                            }
                        ) {
                            Text(
                                "Remove",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveMemberDialog = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Edit Group Name Dialog
            if (showEditNameDialog) {
                AlertDialog(
                    onDismissRequest = { showEditNameDialog = false },
                    title = { Text("Edit Group Name") },
                    text = {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text("Group Name") },
                            supportingText = if (newGroupName.isBlank()){
                                { Text("Group name is required") }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (newGroupName.isNotBlank() && newGroupName != groupInfo?.name) {
                                    viewModel.updateGroupName(groupId, newGroupName.trim())
                                }
                                showEditNameDialog = false
                            },
                            enabled = newGroupName.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditNameDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            error?.let {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(it)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupMemberItem(
    member: GroupMember,
    isAdmin: Boolean,
    currentUserId: String,
    onMessageMember: (String, String, String) -> Unit,
    onViewProfile: () -> Unit,
    onRemoveMember: () -> Unit,
    onMakeAdmin: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (member.userId != currentUserId) {
                    Modifier.clickable { showContextMenu = true }
                } else {
                    Modifier // No interaction for current user
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Member Profile Picture
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val bitmap = remember(member.profileImage) {
                    member.profileImage?.let { base64Image ->
                        try {
                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                            val inputStream = ByteArrayInputStream(imageBytes)
                            BitmapFactory.decodeStream(inputStream)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile picture of ${member.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default profile picture",
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (member.userId == currentUserId) "You" else member.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (member.status.isNotEmpty()) member.status else "Hey there! I am using Temuin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (member.isAdmin) {
                Text(
                    text = "Admin",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Context Menu Dialog
        if (showContextMenu) {
            AlertDialog(
                onDismissRequest = { showContextMenu = false },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Message option
                        TextButton(
                            onClick = {
                                onMessageMember(member.userId, member.name, member.phoneNumber)
                                showContextMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_chat_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Message ${member.name}")
                            }
                        }
                        
                        // View Profile option
                        TextButton(
                            onClick = {
                                onViewProfile()
                                showContextMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("View profile")
                            }
                        }

                        // Make Admin option (only for admin and not for current admin)
                        if (isAdmin && !member.isAdmin) {
                            TextButton(
                                onClick = {
                                    onMakeAdmin()
                                    showContextMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Make ${member.name} as admin")
                                }
                            }
                        }
                        
                        // Remove option (only for admin and not for themselves or other admins)
                        if (isAdmin && !member.isAdmin && member.userId != currentUserId) {
                            TextButton(
                                onClick = {
                                    onRemoveMember()
                                    showContextMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        "Remove ${member.name}",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    friends: List<User>,
    onDismiss: () -> Unit,
    onAddMember: (List<User>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFriends by remember { mutableStateOf(setOf<String>()) }

    // Filter friends based on search query (by name only)
    val filteredFriends = remember(friends, searchQuery) {
        if (searchQuery.isEmpty()) {
            friends
        } else {
            friends.filter { friend ->
                friend.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Members",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (friends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends available to add",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredFriends) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFriends = if (selectedFriends.contains(friend.userId)) {
                                            selectedFriends - friend.userId
                                        } else {
                                            selectedFriends + friend.userId
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Profile Picture
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        val profileBitmap = remember(friend.profileImage) {
                                            friend.profileImage?.let { base64Image ->
                                                try {
                                                    val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                                    val inputStream = ByteArrayInputStream(imageBytes)
                                                    BitmapFactory.decodeStream(inputStream)
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }
                                        }

                                        if (profileBitmap != null) {
                                            Image(
                                                bitmap = profileBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = friend.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = friend.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = selectedFriends.contains(friend.userId),
                                    onCheckedChange = { checked ->
                                        selectedFriends = if (checked) {
                                            selectedFriends + friend.userId
                                        } else {
                                            selectedFriends - friend.userId
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val selectedList = friends.filter { friend -> selectedFriends.contains(friend.userId) }
                        onAddMember(selectedList)
                        onDismiss()
                    },
                    enabled = selectedFriends.isNotEmpty()
                ) {
                    Text("Add Selected")
                }
            }
        }
    )
} 