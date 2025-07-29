package com.temuin.temuin.ui.screens.chats

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.data.model.ChatPreview
import com.temuin.temuin.data.model.User
import com.temuin.temuin.R
import com.temuin.temuin.data.model.MessageStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onChatClick: (String, String, String) -> Unit = { _, _, _ -> },
    onViewProfile: (String) -> Unit = {},
    onGroupChatClick: (String) -> Unit = {},
    onViewGroupProfile: (String) -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    var showErrorDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }
    
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("ChatListScreen", "Lifecycle ON_RESUME: Calling viewModel.onResume()")
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            android.util.Log.d("ChatListScreen", "Lifecycle ON_PAUSE or ON_DESTROY: Removing observer")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isEmpty()) {
            chats.filter { chat ->
                // Hide chats with empty messages (when messaging non-friends creates empty chat rooms)
                chat.lastMessage.isNotBlank()
            }.sortedWith(
                compareByDescending<ChatPreview> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
        } else {
            chats.filter { chat ->
                // Hide chats with empty messages AND apply search filter
                chat.lastMessage.isNotBlank() && chat.name.contains(searchQuery, ignoreCase = true)
            }.sortedWith(
                compareByDescending<ChatPreview> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
        }
    }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = TopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text("Chats", fontWeight = FontWeight.Bold) }
                /*
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { showMenu = false }
                        )
                    }
                }
                */
            )
                
                // Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search chats") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true
                    )
                }
            }
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(painter = painterResource(R.drawable.outline_groups_24), contentDescription = "New Group Chat")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (chats.isEmpty() && !isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No chats yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Start a conversation from your friends list",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (isLoading && chats.isEmpty()) {
                        // Show loading placeholders
                        items(5) {
                            ChatListItemPlaceholder()
                        }
                    } else {
                    items(filteredChats) { chat ->
                        var showContextMenu by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier.combinedClickable(
                            onClick = { 
                                if (chat.isGroup) {
                                    onGroupChatClick(chat.id)
                                } else {
                                    onChatClick(chat.id, chat.name, chat.phoneNumber)
                                }
                            },
                                onLongClick = { showContextMenu = true }
                            )
                        ) {
                            ChatListItem(
                                chat = chat,
                                onViewProfile = {
                                    if (chat.isGroup) {
                                        onViewGroupProfile(chat.id)
                                    } else {
                                        onViewProfile(chat.id)
                                    }
                                }
                            )
                            
                            if (showContextMenu) {
                                AlertDialog(
                                    onDismissRequest = { showContextMenu = false },
                                    text = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Pin/Unpin option
                                            TextButton(
                                                onClick = {
                                                    viewModel.togglePinChat(chat.id)
                                                    showContextMenu = false
                                                },
                                                enabled = !chat.isPinned || viewModel.getPinnedChatsCount() <= 3,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.baseline_pin_24),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(if (chat.isPinned) "Unpin chat" else "Pin chat")
                                                }
                                            }
                                            
                                            // Mark as read/unread
                                            TextButton(
                                                onClick = {
                                                    if (chat.unreadCount > 0) {
                                                        viewModel.markChatAsRead(chat.id)
                                                    } else {
                                                        viewModel.markChatAsUnread(chat.id)
                                                    }
                                                    showContextMenu = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        if (chat.unreadCount > 0) 
                                                            painterResource(R.drawable.outline_done_all_24)
                                                        else 
                                                            painterResource(R.drawable.outline_delivered_all_24),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(if (chat.unreadCount > 0) "Mark as read" else "Mark as unread")
                                                }
                                            }
                                            
                                            // Delete chat
                                            TextButton(
                                                onClick = {
                                                    showDeleteConfirmation = chat.id
                                                    showContextMenu = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(
                                                        "Delete chat",
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {},
                                )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        showDeleteConfirmation?.let { chatId ->
            val chat = chats.find { it.id == chatId }
            val isGroup = chat?.isGroup == true
            
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = null },
                title = { 
                    Text(if (isGroup) "Leave Group" else "Delete Chat") 
                },
                text = { 
                    Text(
                        if (isGroup) 
                            "Are you sure you want to leave this group? You will no longer receive messages from this group."
                        else 
                            "Are you sure you want to delete this chat? This action cannot be undone."
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteChat(chatId)
                            showDeleteConfirmation = null
                        }
                    ) {
                        Text(
                            if (isGroup) "Leave" else "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add CreateGroupDialog
        if (showCreateGroupDialog) {
            CreateGroupDialog(
                onDismiss = { showCreateGroupDialog = false },
                onCreateGroup = { groupName, selectedFriends, profileImage ->
                    viewModel.createGroupChat(groupName, selectedFriends, profileImage)
                    showCreateGroupDialog = false
                }
            )
        }

        // Error Dialog
        error?.let { errorMessage ->
            if (showErrorDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showErrorDialog = false
                        viewModel.clearError()
                    },
                    title = { Text("Error") },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showErrorDialog = false
                                viewModel.clearError()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }

        LaunchedEffect(error) {
            if (error != null) {
                showErrorDialog = true
            }
        }
    }
}

@Composable
fun ChatListItem(
    chat: ChatPreview,
    onViewProfile: () -> Unit
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onViewProfile)
            ) {
                val bitmap = remember(chat.profilePicture) {
                if (chat.profilePicture.isNotEmpty()) {
                        try {
                            val imageBytes = Base64.decode(chat.profilePicture, Base64.DEFAULT)
                            val inputStream = ByteArrayInputStream(imageBytes)
                            BitmapFactory.decodeStream(inputStream)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile picture of ${chat.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DefaultProfileIcon(chat.isGroup)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Chat Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTimestamp(chat.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Handle message status for non-system messages
                        if (chat.isGroup) {
                            val message = chat.lastMessage
                            val isSystemMessage = message.endsWith(" left") || message == "You left" ||
                                               message.endsWith(" joined") || message.startsWith("You added ") ||
                                               message.startsWith("You removed ") || message.endsWith(" was removed") ||
                                               message.startsWith("You: You removed ") || message.contains(" added ") ||
                                               message.startsWith("You are") || message.contains(" is now the group admin") ||
                                               message.endsWith(" the group admin")
                            
                            if (!isSystemMessage && message.startsWith("You:")) {
                                Icon(
                                    painter = when (chat.lastMessageStatus) {
                                        MessageStatus.SENT -> painterResource(R.drawable.baseline_check_24)
                                        MessageStatus.DELIVERED -> painterResource(R.drawable.outline_delivered_all_24)
                                        MessageStatus.READ -> painterResource(R.drawable.outline_done_all_24)
                                    },
                                    contentDescription = "Message status",
                                    modifier = Modifier.size(16.dp),
                                    tint = when (chat.lastMessageStatus) {
                                        MessageStatus.SENT -> Color.Gray
                                        MessageStatus.DELIVERED -> Color.Gray
                                        MessageStatus.READ -> MaterialTheme.colorScheme.inversePrimary
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        } else {
                            // For private chats, show status only if current user is sender
                            if (currentUser?.uid == chat.lastMessageSenderId) {
                                Icon(
                                    painter = when (chat.lastMessageStatus) {
                                        MessageStatus.SENT -> painterResource(R.drawable.baseline_check_24)
                                        MessageStatus.DELIVERED -> painterResource(R.drawable.outline_delivered_all_24)
                                        MessageStatus.READ -> painterResource(R.drawable.outline_done_all_24)
                                    },
                                    contentDescription = "Message status",
                                    modifier = Modifier.size(16.dp),
                                    tint = when (chat.lastMessageStatus) {
                                        MessageStatus.SENT -> Color.Gray
                                        MessageStatus.DELIVERED -> Color.Gray
                                        MessageStatus.READ -> MaterialTheme.colorScheme.inversePrimary
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        
                        // Display the message with appropriate styling
                        if (chat.isGroup) {
                            val message = chat.lastMessage
                            val isSystemMessage = message.endsWith(" left") || message == "You left" ||
                                               message.endsWith(" joined") || message.startsWith("You added ") ||
                                               message.startsWith("You removed ") || message.endsWith(" was removed") ||
                                               message.startsWith("You: You removed ") || message.contains(" added ") ||
                                               message.startsWith("You are") || message.contains(" is now the group admin") ||
                                               message.endsWith(" the group admin")
                            
                            if (isSystemMessage) {
                                // For system messages, remove the sender prefix if it exists
                                val cleanMessage = message.substringAfter(": ", message)
                                
                                // Replace user's name with "You" in system messages only when it matches current user's name
                                val finalMessage = if (currentUser?.displayName != null) {
                                    when {
                                        // When current user is the actor (keep as is)
                                        cleanMessage.startsWith("You added ") -> cleanMessage
                                        cleanMessage.startsWith("You removed ") || cleanMessage.startsWith("You: You removed ") -> 
                                            cleanMessage.replace("You: You removed ", "You removed ")
                                        cleanMessage.startsWith("You Are ") ->
                                            cleanMessage.replace("You: You removed ", "You removed ")
                                        
                                        // When message is about the current user
                                        cleanMessage == "${currentUser.displayName} joined" || 
                                        cleanMessage.matches(".*added ${currentUser.displayName}".toRegex()) -> "You joined"
                                        cleanMessage == "${currentUser.displayName} left" -> "You left"
                                        cleanMessage == "${currentUser.displayName} was removed" -> "You were removed"
                                        cleanMessage == "added ${currentUser.displayName}" -> cleanMessage.replace("${currentUser.displayName}", "You")
                                        cleanMessage == "removed ${currentUser.displayName}" -> cleanMessage.replace("${currentUser.displayName}", "You")
                                        
                                        // For all other cases, keep the original message
                                        else -> cleanMessage
                                    }
                                } else cleanMessage
                                
                                Text(
                                    text = finalMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = chat.lastMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        // Pin indicator
                        if (chat.isPinned) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_pin_24),
                                contentDescription = "Pinned chat",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Unread message count
                        if (chat.unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultProfileIcon(isGroup: Boolean) {
    Icon(
        painter = if (isGroup) painterResource(R.drawable.outline_groups_24) else painterResource(R.drawable.outline_person_24),
        contentDescription = if (isGroup) "Group chat icon" else "Default profile picture",
        modifier = Modifier
            .padding(12.dp)
            .fillMaxSize(),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreateGroup: (String, List<User>, String?) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    val selectedFriends = remember { mutableStateListOf<User>() }
    var groupProfileImage by remember { mutableStateOf<String?>(null) }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmapImage by remember { mutableStateOf<Bitmap?>(null) }
    var showAddParticipant by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profileImageUri = uri
        uri?.let {
            bitmapImage = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            }
            // Convert bitmap to base64 string
            bitmapImage?.let { bitmap ->
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                groupProfileImage = Base64.encodeToString(byteArray, Base64.DEFAULT)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Group Chat") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Group Profile Picture
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmapImage != null) {
                        Image(
                            bitmap = bitmapImage!!.asImageBitmap(),
                            contentDescription = "Group profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_groups_24),
                                contentDescription = "Add photo",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Add photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Group Name Input
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    supportingText = if (groupName.isBlank()) {
                        { Text("Group name is required") }
                    } else null,
                    isError = groupName.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Members Section
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Members",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (selectedFriends.isNotEmpty()) {
                                        selectedFriends.forEach { friend ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Profile Picture
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
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
                                                            text = friend.name.toString(),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )

                                                        Text(
                                                            text = friend.phoneNumber.toString(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                }
                                                IconButton(
                                                    onClick = {
                                                        selectedFriends.remove(friend)
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove member",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    TextButton(
                                        onClick = { showAddParticipant = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(12.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                "Add Members",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }


                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (groupName.isNotBlank() && selectedFriends.isNotEmpty()) {
                        onCreateGroup(groupName, selectedFriends.toList(), groupProfileImage)
                    }
                },
                enabled = groupName.isNotBlank() && selectedFriends.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showAddParticipant) {
        AddMembersDialog(
            onDismiss = { showAddParticipant = false },
            onMembersSelected = { newMembers ->
                selectedFriends.addAll(newMembers.filter { friend -> !selectedFriends.contains(friend) })
            },
            viewModel = viewModel,
            existingMembers = selectedFriends.map { it.userId }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMembersDialog(
    onDismiss: () -> Unit,
    onMembersSelected: (List<User>) -> Unit,
    viewModel: ChatListViewModel,
    existingMembers: List<String>
) {
    var searchQuery by remember { mutableStateOf("") }
    val friends by viewModel.friends.collectAsState()
    var selectedUsers by remember { mutableStateOf(existingMembers.toSet()) }

    // Load friends when dialog opens
    LaunchedEffect(Unit) {
        viewModel.loadFriends()
    }

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
                            text = "No friends found",
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
                                        selectedUsers = if (selectedUsers.contains(friend.userId)) {
                                            selectedUsers - friend.userId
                                        } else {
                                            selectedUsers + friend.userId
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
                                    checked = selectedUsers.contains(friend.userId),
                                    onCheckedChange = { checked ->
                                        selectedUsers = if (checked) {
                                            selectedUsers + friend.userId
                                        } else {
                                            selectedUsers - friend.userId
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
                        val selectedList = friends.filter { friend -> selectedUsers.contains(friend.userId) }
                        onMembersSelected(selectedList)
                        onDismiss()
                    }
                ) {
                    Text("Add Selected")
                }
            }
        }
    )
}

internal fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Now" // less than 1 minute
        diff < 3600_000 -> "${diff / 60_000}m" // less than 1 hour
        diff < 86400_000 -> "${diff / 3600_000}h" // less than 24 hours
        diff < 604800_000 -> "${diff / 86400_000}d" // less than 7 days
        else -> {
            val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
            date
        }
    }
}

@Composable
private fun ChatListItemPlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Chat Info Placeholders
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Name Placeholder
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.small
                            )
                    )
                    // Time Placeholder
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.small
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Message Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}