package com.temuin.temuin.ui.screens.groupchat

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.temuin.temuin.data.model.MessageStatus
import com.temuin.temuin.R
import java.io.ByteArrayInputStream
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.temuin.temuin.data.model.GroupChatMessage
import com.temuin.temuin.data.model.GroupInfo
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import kotlin.compareTo
import kotlin.rem
import kotlin.text.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit,
    onViewGroupProfile: (String) -> Unit,
    onViewProfile: (String) -> Unit = {},
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
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
                // Consider "at bottom" if the last visible item is within 3 items of the last message
                lastVisibleItem.index >= messages.size - 1 - 3
            }
        }
    }

    // Track if we should load more messages
    val shouldLoadMore by remember {
        derivedStateOf {
            if (listState.layoutInfo.visibleItemsInfo.isEmpty()) {
                false
            } else {
                // Load more when first visible item is close to the top
                val firstVisibleItem = listState.firstVisibleItemIndex
                firstVisibleItem < 5 && !isLoadingMore && messages.isNotEmpty()
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
        }.reversed() // Reverse to get latest messages first
    }
    
    var currentSearchIndex by remember { mutableStateOf(0) }

    // Effect to load more messages when scrolling up
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreMessages()
        }
    }

    // Effect to scroll to search result when search is active
    LaunchedEffect(searchQuery, searchResults) {
        if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
            currentSearchIndex = 0
            listState.animateScrollToItem(searchResults[0])
        }
    }

    // Effect to scroll to bottom when keyboard appears
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && !wasKeyboardVisible && !isInitialLoad && messages.isNotEmpty() && !isSearchActive) {
            // Keyboard just appeared - scroll to bottom like WhatsApp
            kotlinx.coroutines.delay(200) // Slightly longer delay for keyboard animation
            listState.animateScrollToItem(messages.size - 1)
        }
        wasKeyboardVisible = isKeyboardVisible
    }

    LaunchedEffect(groupId) {
        viewModel.loadGroupChat(groupId)
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

    // Effect to scroll to bottom on initial load
    LaunchedEffect(messages.size, isLoading) {
        if (!isLoading && messages.isNotEmpty() && isInitialLoad) {
            delay(100)
            listState.scrollToItem(messages.size - 1)
            isInitialLoad = false
        }
    }

    // Handle new messages after initial load
    LaunchedEffect(messages.size) {
        if (!isInitialLoad && messages.isNotEmpty() && !isSearchActive) {
            val isNewMessage = messages.size > previousMessageCount
            val lastMessage = messages.lastOrNull()
            val isFromCurrentUser = lastMessage?.isFromCurrentUser == true
            
            if (isNewMessage && (isFromCurrentUser || isAtBottom)) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
            
            previousMessageCount = messages.size
        }
    }

    LaunchedEffect(groupId) {
        viewModel.loadGroupChat(groupId)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0),
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
                                .clickable { onViewGroupProfile(groupId) }
                        ) {
                            // Group Profile Picture
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onViewGroupProfile(groupId) }
                            ) {
                                val bitmap = remember(groupInfo?.profileImage) {
                                    groupInfo?.profileImage?.let { base64Image ->
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
                                        contentDescription = "Group profile picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_groups_24),
                                        contentDescription = "Default group icon",
                                        modifier = Modifier.padding(8.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = groupInfo?.name ?: "Loading...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${groupInfo?.members?.size ?: 0} members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                                text = { Text("View Group") },
                                onClick = { 
                                    onViewGroupProfile(groupId)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
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
                // Messages List
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> {
                            // Show loading indicator when initially loading
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Loading messages...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        /*messages.isEmpty() -> {
                            // Show empty state when no messages
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_groups_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "No messages yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Start the conversation in ${groupInfo?.name ?: "this group"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }*/
                        else -> {
                            // Show messages list
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Loading more indicator at the top
                                if (isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }

                                // Group creation notification
                                if (groupInfo != null && messages.isNotEmpty()) {
                                    val groupCreatedMessage = messages.find { it.content == "Group created" }
                                    if (groupCreatedMessage != null) {
                                        item {
                                            GroupCreationNotification(
                                                groupInfo = groupInfo!!,
                                                creatorId = groupCreatedMessage.senderId,
                                                currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                            )
                                        }
                                    }
                                }
                                
                                // Messages
                                items(messages) { message ->
                                    // Skip the "Group created" message as it's handled by the notification above
                                    if (message.content == "Group created") return@items
                                    
                                    // Check if this is a "user left" message and render as notification
                                    if (message.content.endsWith(" left") || message.content == "You left") {
                                        UserLeftNotification(
                                            message = message,
                                            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                        )
                                        return@items
                                    }
                                    
                                    // Check if this is a "user joined" message and render as notification
                                    if (message.content.endsWith(" joined")) {
                                        UserJoinedNotification(
                                            message = message,
                                            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                        )
                                        return@items
                                    }
                                    
                                    // Check if this is a "user removed" message and render as notification
                                    if (message.content.startsWith("You removed ") || message.content.endsWith(" was removed")) {
                                        UserRemovedNotification(
                                            message = message,
                                            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                        )
                                        return@items
                                    }
                                    
                                    // Check if this is an admin change message
                                    if (message.content.contains(" is now the group admin")) {
                                        AdminChangedNotification(
                                            message = message,
                                            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                        )
                                        return@items
                                    }
                                    
                                    val isCurrentSearchResult = if (searchResults.isNotEmpty()) {
                                        messages.indexOf(message) == searchResults[currentSearchIndex]
                                    } else false
                                    
                                    GroupChatMessageItem(
                                        message = message,
                                        isFromCurrentUser = message.isFromCurrentUser,
                                        searchQuery = searchQuery,
                                        isHighlighted = isCurrentSearchResult,
                                        onProfileClick = { onViewProfile(message.senderId) },
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
                }

                // Message Input (moved from bottomBar to here) - only show when not searching
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
                                    maxLines = 5,
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
                                        viewModel.sendMessage(messageText)
                                        messageText = ""
                                    }
                                },
                                enabled = messageText.isNotBlank(),
                                modifier = Modifier.size(50.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Default.Send,
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
                        .padding(bottom = 16.dp)
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

            error?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Snackbar {
                        Text(it)
                    }
                }
            }
        }
    }
}

@Composable
fun GroupChatMessageItem(
    message: GroupChatMessage,
    isFromCurrentUser: Boolean,
    searchQuery: String = "",
    isHighlighted: Boolean = false,
    onProfileClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromCurrentUser) {
            Spacer(modifier = Modifier.width(12.dp))
            // Profile Picture with click
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onProfileClick)
            ) {
                val bitmap = remember(message.senderProfileImage) {
                    message.senderProfileImage?.let { base64Image ->
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
                        contentDescription = "Profile picture of ${message.senderName}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(
            horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
        ) {
            if (!isFromCurrentUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onProfileClick)
                    )
                }
            }
            
            Surface(
                color = if (isFromCurrentUser) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = if (isFromCurrentUser) 12.dp else 4.dp,
                    topEnd = if (isFromCurrentUser) 4.dp else 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                ),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clickable(onClick = onClick)
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
                        // Highlight matching text
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
                                        color = if (isFromCurrentUser) 
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
                            color = if (isFromCurrentUser) 
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
                        Text(
                            text = message.formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFromCurrentUser) 
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        // Only show status for current user's messages
                        if (isFromCurrentUser) {
                            Icon(
                                painter = when (message.status) {
                                    MessageStatus.SENT -> painterResource(R.drawable.baseline_check_24)  // Single check
                                    MessageStatus.DELIVERED -> painterResource(R.drawable.outline_delivered_all_24)  // Double check
                                    MessageStatus.READ -> painterResource(R.drawable.outline_done_all_24)  // Double check with color
                                },
                                contentDescription = when (message.status) {
                                    MessageStatus.SENT -> "Message sent"
                                    MessageStatus.DELIVERED -> "Message delivered to all"
                                    MessageStatus.READ -> "Message read by all"
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

        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
fun GroupCreationNotification(
    groupInfo: GroupInfo,
    creatorId: String,
    currentUserId: String
) {
    val isCurrentUser = creatorId == currentUserId
    var creatorName by remember { mutableStateOf("Someone") }
    
    // Get creator's name if it's not the current user
    LaunchedEffect(creatorId) {
        if (!isCurrentUser) {
            // Fetch creator's name from Firebase
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(creatorId)
                .child("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        creatorName = name
                    }
                }
                .addOnFailureListener {
                    // Keep default value if fetch fails
                    creatorName = "Someone"
                }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Group Profile Picture
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val bitmap = remember(groupInfo.profileImage) {
                        groupInfo.profileImage?.let { base64Image ->
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
                            contentDescription = "Group profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.outline_groups_24),
                            contentDescription = "Group icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Creator text
                Text(
                    text = if (isCurrentUser) "You created this group" else "$creatorName created this group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                // Group info
                Text(
                    text = "Group • ${groupInfo.members.size} members",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun UserLeftNotification(
    message: GroupChatMessage,
    currentUserId: String
) {
    val isCurrentUser = message.senderId == currentUserId
    var userName by remember { mutableStateOf("Someone") }
    
    // Get user's name if it's not the current user
    LaunchedEffect(message.senderId) {
        if (!isCurrentUser) {
            // Fetch user's name from Firebase
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(message.senderId)
                .child("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        userName = name
                    }
                }
                .addOnFailureListener {
                    // Keep default value if fetch fails
                    userName = "Someone"
                }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User profile picture (smaller for left notification)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val bitmap = remember(message.senderProfileImage) {
                        message.senderProfileImage?.let { base64Image ->
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
                            contentDescription = "User profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "User icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Left notification text
                Text(
                    text = if (isCurrentUser) "You left" else "$userName left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Timestamp
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun UserJoinedNotification(
    message: GroupChatMessage,
    currentUserId: String
) {
    val isCurrentUser = message.senderId == currentUserId
    var userName by remember { mutableStateOf("Someone") }
    
    // Extract the added user name from the message content (e.g., "Alvin joined")
    val joinedUserName = message.content.removeSuffix(" joined")
    val addedUserId = message.addedUserId
    val currentViewingUserId = FirebaseAuth.getInstance().currentUser?.uid
    
    // Get the person who added them (message sender)
    LaunchedEffect(message.senderId) {
        if (!isCurrentUser) {
            // Fetch sender's name from Firebase (person who added)
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(message.senderId)
                .child("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        userName = name
                    }
                }
                .addOnFailureListener {
                    userName = "Someone"
                }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group icon for join notifications
                Icon(
                    painter = painterResource(R.drawable.outline_groups_24),
                    contentDescription = "Group join",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Join notification text
                val displayJoinedName = if (addedUserId == currentViewingUserId) "You" else joinedUserName
                Text(
                    text = if (isCurrentUser) "You added $displayJoinedName" else "$userName added $displayJoinedName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Timestamp
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun UserRemovedNotification(
    message: GroupChatMessage,
    currentUserId: String
) {
    val isCurrentUser = message.senderId == currentUserId
    var userName by remember { mutableStateOf("Someone") }
    var removedUserName by remember { mutableStateOf("Someone") }
    val currentViewingUserId = FirebaseAuth.getInstance().currentUser?.uid
    
    // Extract names from the message content
    when {
        message.content.startsWith("You removed ") -> {
            removedUserName = message.content.removePrefix("You removed ")
        }
        message.content.endsWith(" was removed") -> {
            removedUserName = message.content.removeSuffix(" was removed")
        }
    }
    
    // If the removed user is the current viewing user, show "You" instead
    val displayRemovedName = if (message.removedUserId == currentViewingUserId) "You" else removedUserName
    
    // Get the person who removed them (message sender)
    LaunchedEffect(message.senderId) {
        if (!isCurrentUser) {
            // Fetch sender's name from Firebase (person who removed)
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(message.senderId)
                .child("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        userName = name
                    }
                }
                .addOnFailureListener {
                    userName = "Someone"
                }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Remove icon for remove notifications
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Member removed",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                // Remove notification text
                Text(
                    text = if (isCurrentUser) {
                        if (displayRemovedName == "You") "You removed yourself" else "You removed $displayRemovedName"
                    } else {
                        if (displayRemovedName == "You") "$userName removed you" else "$userName removed $displayRemovedName"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Timestamp
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AdminChangedNotification(
    message: GroupChatMessage,
    currentUserId: String
) {
    val isCurrentUser = message.senderId == currentUserId
    var userName by remember { mutableStateOf("Someone") }
    var newAdminName by remember { mutableStateOf("Someone") }
    val currentViewingUserId = FirebaseAuth.getInstance().currentUser?.uid
    
    // Extract the new admin name from the message content
    val newAdminId = message.newAdminId
    
    // Get the person who changed admin (message sender)
    LaunchedEffect(message.senderId) {
        if (!isCurrentUser) {
            // Fetch sender's name from Firebase (person who changed admin)
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(message.senderId)
                .child("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        userName = name
                    }
                }
                .addOnFailureListener {
                    userName = "Someone"
                }
        }
    }
    
    // Get new admin's name
    LaunchedEffect(newAdminId) {
        if (newAdminId != null) {
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(newAdminId)
                .child("name")
                .get()
                .addOnSuccessListener { snapshot ->
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        newAdminName = name
                    }
                }
                .addOnFailureListener {
                    newAdminName = "Someone"
                }
        }
    }
    
    // If the new admin is the current viewing user, show "You" instead
    val displayNewAdminName = if (newAdminId == currentViewingUserId) "You" else newAdminName
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Admin icon for admin change notifications
                Icon(
                    Icons.Default.Build,
                    contentDescription = "Admin changed",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                // Admin change notification text
                Text(
                    text = if (isCurrentUser) {
                        if (displayNewAdminName == "You") "You are now the group admin"
                        else "You made $displayNewAdminName the group admin"
                    } else {
                        if (displayNewAdminName == "You") "$userName made you the group admin"
                        else "$userName made $displayNewAdminName the group admin"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Timestamp
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}