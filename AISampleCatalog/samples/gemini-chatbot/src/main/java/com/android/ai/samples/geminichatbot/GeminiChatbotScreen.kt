package com.android.ai.samples.geminichatbot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatbotScreen (
    viewModel: GeminiChatbotViewModel = hiltViewModel()
) {

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)

    val messages by viewModel.messageList.collectAsState()

    var message by rememberSaveable { mutableStateOf("") }

    Scaffold (
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(title = {
                Text(text = stringResource(id = R.string.geminichatbot_title_bar))
            })
        }
    ){ innerPadding ->
        Column {
            val layoutDirection = LocalLayoutDirection.current
            MessageList(
                messages = messages.sortedBy { - it.timestamp },
                contentPadding = innerPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            InputBar(
                value = message,
                placeholder = stringResource(R.string.geminichatbot_input_placeholder),
                onInputChanged = {
                    message = it
                },
                onSendClick = {
                    viewModel.sendMessage(message)
                    message = ""
                },
                contentPadding = innerPadding.copy(layoutDirection, top = 0.dp),
                sendEnabled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun PaddingValues.copy(
    layoutDirection: LayoutDirection,
    start: Dp? = null,
    top: Dp? = null,
    end: Dp? = null,
    bottom: Dp? = null,
) = PaddingValues(
    start = start ?: calculateStartPadding(layoutDirection),
    top = top ?: calculateTopPadding(),
    end = end ?: calculateEndPadding(layoutDirection),
    bottom = bottom ?: calculateBottomPadding(),
)

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn (
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ){
        items(items = messages) { message ->
            Row (
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    16.dp,
                    if (message.isIncoming) Alignment.Start else Alignment.End
                ),
                verticalAlignment = Alignment.CenterVertically
            ){
                val iconSize = 48.dp
                Spacer(modifier = Modifier.size(iconSize))
                MessageBubble(
                    message = message
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (message.isIncoming) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        shape = MaterialTheme.shapes.large
    ) {
        Column (
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = message.text)
        }
    }
}