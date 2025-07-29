package com.temuin.temuin.ui.screens.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.temuin.temuin.data.model.MessageStatus
import com.temuin.temuin.R
import com.temuin.temuin.data.model.ChatMessage
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    recipientId: String,
    recipientName: String,
    onNavigateBack: () -> Unit,
    onViewProfile: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val profilePicture by viewModel.recipientProfilePicture.collectAsState()
    val isRecipientFriend by viewModel.isRecipientFriend.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val density = LocalDensity.current

    // Track keyboard visibility
    val imeInsets = WindowInsets.ime
    val isKeyboardVisible by remember {
        derivedStateOf {
            imeInsets.getBottom(density) > 0
        }
    }

    // Track if user is at bottom of the list
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                true
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                val lastItem = messages.size - 1
                // Consider "at bottom" if the last visible item is within 3 items of the last message
                lastVisibleItem.index >= lastItem - 3
            }
        }
    }

    // Track if this is the initial load
    var isInitialLoad by remember { mutableStateOf(true) }
    
    // Track the previous message count to detect new messages
    var previousMessageCount by remember { mutableStateOf(0) }

    // Track previous keyboard state to detect keyboard show/hide
    var wasKeyboardVisible by remember { mutableStateOf(false) }

    // Track search results
    val searchResults = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else messages.mapIndexedNotNull { index, message ->
            if (message.content.contains(searchQuery, ignoreCase = true)) index
            else null
        }
    }.reversed() // Reverse to get latest messages first
    
    var currentSearchIndex by remember { mutableStateOf(0) }

    // Effect to scroll to first result when starting a new search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
            currentSearchIndex = 0
            listState.animateScrollToItem(searchResults[0])
        }
    }

    // Effect to scroll to bottom when keyboard appears
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && !wasKeyboardVisible && !isInitialLoad && messages.isNotEmpty() && !isSearchActive) {
            // Keyboard just appeared - scroll to bottom
            kotlinx.coroutines.delay(200) // Slightly longer delay for keyboard animation
            listState.animateScrollToItem(messages.size - 1)
        }
        wasKeyboardVisible = isKeyboardVisible
    }

    // Function to scroll to next search result
    fun scrollToNextSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
            coroutineScope.launch {
                listState.animateScrollToItem(searchResults[currentSearchIndex])
            }
        }
    }

    // Function to scroll to previous search result
    fun scrollToPreviousSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = if (currentSearchIndex > 0) currentSearchIndex - 1 else searchResults.size - 1
            coroutineScope.launch {
                listState.animateScrollToItem(searchResults[currentSearchIndex])
            }
        }
    }

    // Function to scroll to bottom
    fun scrollToBottom() {
        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Add this remember block for profile picture bitmap
    val profileBitmap = remember(profilePicture) {
        profilePicture?.let { base64Image ->
            try {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val inputStream = ByteArrayInputStream(imageBytes)
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Notify the chat is being viewed and setup message delivery tracking
    LaunchedEffect(Unit) {
        viewModel.loadMessages(recipientId)
        viewModel.loadRecipientProfile(recipientId)
        viewModel.enterChat(recipientId)
    }
    
    // Clean up when leaving the chat
    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveChat()
        }
    }

    // Handle initial scroll when loading is complete
    LaunchedEffect(isLoading, messages.size) {
        if (!isLoading && messages.isNotEmpty() && isInitialLoad) {
            // Wait a bit for UI to settle, then scroll to bottom
            kotlinx.coroutines.delay(100)
            listState.scrollToItem(messages.size - 1)
            isInitialLoad = false
        }
    }

    // Handle new messages after initial load
    LaunchedEffect(messages.size) {
        if (!isInitialLoad && messages.isNotEmpty() && !isSearchActive) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val isNewMessage = messages.size > previousMessageCount
            val lastMessage = messages.lastOrNull()
            val isFromCurrentUser = lastMessage?.senderId == currentUser?.uid
            
            if (isNewMessage && (isFromCurrentUser || isAtBottom)) {
                // Scroll to bottom for new messages if:
                // 1. Message is from current user, OR
                // 2. User is already at bottom
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
            }
            
            previousMessageCount = messages.size
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        if (isSearchActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { 
                                        searchQuery = it
                                        viewModel.searchMessages(it)
                                    },
                                    placeholder = { Text("Search messages") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { 
                                                isSearchActive = false
                                                searchQuery = ""
                                                viewModel.clearSearch()
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge,
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .clickable { onViewProfile(recipientId) }
                            ) {
                                // Profile Picture
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onViewProfile(recipientId) }
                                ) {
                                    if (profileBitmap != null) {
                                        Image(
                                            bitmap = profileBitmap.asImageBitmap(),
                                            contentDescription = "Profile picture of $recipientName",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        DefaultProfileIcon()
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    text = recipientName,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Search") },
                                    onClick = { 
                                        isSearchActive = true
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Profile") },
                                    onClick = { 
                                        onViewProfile(recipientId)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.ime)
                ) {
                    // Add NotFriendBanner if recipient is not a friend
                    if (!isRecipientFriend) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$recipientName is not in your friend list",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.addAsFriend(recipientId) {
                                            // Optional: Show success message or handle completion
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Add Friend")
                                }
                            }
                        }
                        // Spacing after the notification banner
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Messages List
                    if (isLoading && !messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (messages.isEmpty()) {
                        // No chat history message
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "No chat history",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Start a conversation with $recipientName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages) { message ->
                                val isCurrentUser = message.senderId == currentUser?.uid
                                val isCurrentSearchResult = if (searchResults.isNotEmpty()) {
                                    messages.indexOf(message) == searchResults[currentSearchIndex]
                                } else false
                                
                                ChatMessageItem(
                                    message = message,
                                    isCurrentUser = isCurrentUser,
                                    searchQuery = searchQuery,
                                    isHighlighted = isCurrentSearchResult,
                                    onClick = {
                                        if (searchQuery.isNotEmpty() && message.content.contains(searchQuery, ignoreCase = true)) {
                                            val index = searchResults.indexOfFirst { resultIndex ->
                                                messages[resultIndex] == message
                                            }
                                            if (index != -1) {
                                                currentSearchIndex = index
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(searchResults[index])
                                                }
                                            }
                                        }
                                    }
                                )
                                }
                            }

                            // Scroll to bottom button
                            if (!isAtBottom && messages.isNotEmpty() && !isSearchActive) {
                                FloatingActionButton(
                                    onClick = { scrollToBottom() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll to bottom"
                                    )
                                }
                            }
                        }
                    }

                    // Message Input - only show when not searching
                    if (!isSearchActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ) {
                                    OutlinedTextField(
                                        value = messageText,
                                        onValueChange = { messageText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused && !isInitialLoad && messages.isNotEmpty() && !isSearchActive) {
                                                    // Text field got focus - scroll to bottom
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(messages.size - 1)
                                                    }
                                                }
                                            },
                                        placeholder = { Text("Type a message") },
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent
                                        ),
                                        textStyle = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                FilledIconButton(
                                    onClick = {
                                        if (messageText.isNotBlank()) {
                                            viewModel.sendMessage(recipientId, messageText)
                                            messageText = ""
                                        }
                                    },
                                    enabled = messageText.isNotBlank(),
                                    modifier = Modifier.size(50.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send message",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Search navigation overlay
                if (isSearchActive && searchResults.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp) // Reduced padding since no message input
                    ) {
                        Surface(
                            modifier = Modifier.padding(16.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${currentSearchIndex + 1} of ${searchResults.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { scrollToNextSearchResult() },
                                    enabled = searchResults.size > 1
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Previous result",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { scrollToPreviousSearchResult() },
                                    enabled = searchResults.size > 1
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Next result",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error Dialog
            error?.let { errorMessage ->
                ErrorDialog(
                    error = errorMessage,
                    showDialog = showErrorDialog,
                    onDismiss = {
                        showErrorDialog = false
                        viewModel.clearError()
                    }
                )
            }

            LaunchedEffect(error) {
                if (error != null) {
                    showErrorDialog = true
                }
            }
        }
    }
}

@Composable
private fun DefaultProfileIcon() {
    Icon(
        imageVector = Icons.Default.Person,
        contentDescription = "Default profile picture",
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize(),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isCurrentUser: Boolean,
    searchQuery: String = "",
    isHighlighted: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = if (isCurrentUser) 12.dp else 4.dp,
                    topEnd = if (isCurrentUser) 4.dp else 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                ),
                modifier = Modifier
                    .then(
                        if (searchQuery.isNotEmpty() && message.content.contains(searchQuery, ignoreCase = true))
                            Modifier.clickable(onClick = onClick)
                        else
                            Modifier
                    )
                    .then(
                        if (isHighlighted) 
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.inversePrimary,
                                RoundedCornerShape(12.dp)
                            )
                        else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (searchQuery.isNotEmpty() && message.content.contains(searchQuery, ignoreCase = true)) {
                        val annotatedString = buildAnnotatedString {
                            val text = message.content
                            var startIndex = 0
                            val searchPattern = searchQuery.lowercase()
                            val messageLower = text.lowercase()
                            
                            while (true) {
                                val index = messageLower.indexOf(searchPattern, startIndex)
                                if (index == -1) {
                                    append(text.substring(startIndex))
                                    break
                                }
                                append(text.substring(startIndex, index))
                                withStyle(
                                    style = SpanStyle(
                                        background = MaterialTheme.colorScheme.inversePrimary,
                                        color = if (isCurrentUser) 
                                            MaterialTheme.colorScheme.onPrimary 
                                        else 
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append(text.substring(index, index + searchQuery.length))
                                }
                                startIndex = index + searchQuery.length
                            }
                        }
                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = message.content,
                            color = if (isCurrentUser) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        // Only show status for current user's messages

                        
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrentUser) 
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        if (isCurrentUser) {
                            Icon(
                                painter = when (message.status) {
                                    MessageStatus.SENT -> painterResource(R.drawable.baseline_check_24)  // Single check
                                    MessageStatus.DELIVERED -> painterResource(R.drawable.outline_delivered_all_24)  // Double check
                                    MessageStatus.READ -> painterResource(R.drawable.outline_done_all_24)  // Double check with color
                                },
                                contentDescription = when (message.status) {
                                    MessageStatus.SENT -> "Message sent"
                                    MessageStatus.DELIVERED -> "Message delivered"
                                    MessageStatus.READ -> "Message read"
                                },
                                modifier = Modifier.size(16.dp),
                                tint = when (message.status) {
                                    MessageStatus.SENT -> Color.Gray
                                    MessageStatus.DELIVERED -> Color.Gray
                                    MessageStatus.READ -> MaterialTheme.colorScheme.inversePrimary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun ErrorDialog(
    error: String,
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
} 