package com.android.ai.samples.geminimultimodal

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

class GeminiMultimodalViewModel @Inject constructor(): ViewModel() {

    private val _textGenerated = MutableStateFlow("")
    val textGenerated: StateFlow<String> = _textGenerated

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val generativeModel = Firebase.vertexAI.generativeModel(
        "gemini-1.5-flash",
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
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE))
        )

    fun generate(
        bitmap: Bitmap,
        prompt: String
    ) {
        _isGenerating.value = true

        val multimodalPrompt = content {
            image(bitmap)
            text(prompt?:"Describe this picture")
        }
        viewModelScope.launch {
            val result = generativeModel.generateContent(multimodalPrompt)

            result.text?.let {
                _textGenerated.value = result.text!!
                _isGenerating.postValue(false)
            }
        }
    }
}