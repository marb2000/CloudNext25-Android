package com.android.ai.samples.geminichatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.HarmBlockThreshold
import com.google.firebase.vertexai.type.HarmCategory
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class GeminiChatbotViewModel @Inject constructor(): ViewModel() {

    private val _messageList = MutableStateFlow(mutableListOf<ChatMessage>())
    val messageList: StateFlow<List<ChatMessage>> = _messageList

    private val generativeModel = Firebase.vertexAI.generativeModel(
        "gemini-1.5-pro",
        generationConfig = generationConfig {
            temperature = 0.9f
            topK = 32
            topP = 1f
            maxOutputTokens = 4096
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE)
        ),
        systemInstruction = content {
            """You are a friendly assistant. Keep your response short."""
        }
    )

    private val chat = generativeModel.startChat()

    fun sendMessage(message: String) {
        viewModelScope.launch {
            _messageList.value.add(
                ChatMessage(
                    message,
                    System.currentTimeMillis(),
                    false,
                    null
                )
            )

            val response = chat.sendMessage(message)

            response.text?.let {
                _messageList.value = _messageList.value.toMutableList().apply {
                    add(
                        ChatMessage(
                            it.trim(),
                            System.currentTimeMillis(),
                            true,
                            null
                        )
                    )
                }
            }
        }
    }
}