package com.android.ai.samples.geminichatbot

import android.net.Uri

data class ChatMessage(
    val text: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val senderIconUrl: Uri?
)