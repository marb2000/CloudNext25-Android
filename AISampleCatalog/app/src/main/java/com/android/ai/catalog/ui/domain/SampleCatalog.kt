package com.android.ai.catalog.ui.domain

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.android.ai.catalog.R
import com.android.ai.samples.geminichatbot.GeminiChatbotScreen
import com.android.ai.samples.geminichatbot.GeminiChatbotViewModel
import com.android.ai.samples.geminimultimodal.GeminiMultimodalScreen

class SampleCatalog(
    modifier: Modifier,
    context: Context
) {

    val list = listOf(
        SampleCatalogItem(
            title = context.getString(R.string.gemini_multimodal_sample_title),
            description = context.getString(R.string.gemini_multimodal_sample_description),
            route = "GeminiMultimodalScreen",
            sampleEntryScreen = { GeminiMultimodalScreen(modifier = modifier) }
        ),
        SampleCatalogItem(
            title = context.getString(R.string.gemini_chatbot_sample_title),
            description = context.getString(R.string.gemini_chatbot_sample_description),
            route = "GeminiChitchatScreen",
            sampleEntryScreen = { GeminiChatbotScreen() }
        )
        // To create a new sample entry, add a new SampleCatalogItem here.
    )

}

data class SampleCatalogItem(
    val title: String,
    val description: String,
    val route: String,
    val sampleEntryScreen: @Composable () -> Unit
)