package com.example.bidisampleapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.ai.vertexai.GenerativeBackend
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.FunctionCallPart
import com.google.firebase.vertexai.type.FunctionDeclaration
import com.google.firebase.vertexai.type.FunctionResponsePart
import com.google.firebase.vertexai.type.LiveContentResponse.Status
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.MediaData
import com.google.firebase.vertexai.type.PublicPreviewAPI
import com.google.firebase.vertexai.type.ResponseModality
import com.google.firebase.vertexai.type.Schema
import com.google.firebase.vertexai.type.SpeechConfig
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.Voices
import com.google.firebase.vertexai.type.asInlineDataPartOrNull
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.liveGenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(PublicPreviewAPI::class)
class MainActivity : ComponentActivity() {

    var session: LiveSession? = null

    val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    val playBackQueue = ConcurrentLinkedQueue<ByteArray>()
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var rootView: View
    private lateinit var colorCard: MaterialCardView
    private lateinit var buttonsCard: MaterialCardView
    private lateinit var currentColorText: TextView
    private lateinit var waveformView: AudioWaveformView

    private val defaultBackgroundColor = "#1E293B"
    private var currentColorHex = "#3B82F6"
    private var isListening = false

    // Material Design colors
    private val googleBlue = Color.parseColor("#4285F4")
    private val googleGreen = Color.parseColor("#34A853")
    private val googleYellow = Color.parseColor("#FBBC05")
    private val googleRed = Color.parseColor("#EA4335")
    private val materialGrey = Color.parseColor("#757575")

    fun changeBackgroundColor(hexColor: String) {
        runOnUiThread {
            try {
                val color = Color.parseColor(hexColor)
                currentColorHex = hexColor

                colorCard.setCardBackgroundColor(color)

                updateColorHexDisplay(hexColor)

            } catch (e: IllegalArgumentException) {
                updateColorHexDisplay("Invalid")
            }
        }
    }

    private fun updateColorHexDisplay(hexColor: String) {
        currentColorText.text = "Color: $hexColor"
    }

    suspend fun liveAPISetup() {
        val liveGenerationConfig = liveGenerationConfig {
            speechConfig = SpeechConfig(voice = Voices.UNSPECIFIED)
            responseModality = ResponseModality.AUDIO
        }

        val systemInstruction = content("user") {
            text(
                """
        **Your Role:** You are a friendly and helpful voice assistant in this app. 
        Your main job is to change the app's color based on user requests.

        **Interaction Steps:**
        1.  **Greeting (First turn ONLY):** Start the very first interaction with a friendly 
            greeting like: "Hi! I'm your AI color assistant, ready to help. What color would you like to see?" 
            (Adapt naturally, don't repeat this exact phrase every time).

        2.  **Understand Request:** Listen for the user asking for a specific color (e.g., "blue", "emerald green") 
            or an abstract theme/concept (e.g., "ocean", "happiness", "sunset").

        3.  **Determine HEX Code:** Figure out the appropriate hexadecimal color code for the request.
            * For specific colors, use the standard HEX code.
            * For themes/concepts, be creative! Choose a representative HEX code (e.g., sunset ~ #FF7E5F, ocean ~ #1A5276, forest ~ #1E8449, happiness ~ #FBBC05).

        4.  **MANDATORY ACTION:** You **MUST ALWAYS** call the `changeBackgroundColor` function with the chosen HEX code in the `hexColor` parameter. This is how you change the color in the app.

        5.  **Verbal Response Rules:** After calling the function, respond verbally to the user:
            * **CRITICAL:** **NEVER SAY THE HEX CODE** (e.g., "#FF7E5F") out loud to the user. Do not mention "hex" or "hexadecimal".
            * **Explain Abstract Choices:** If the request was a theme/concept, briefly explain *why* you chose that color (e.g., "Okay, bringing up a deep blue, like the vast ocean!").
            * **Confirm Specific Colors:** If the request was a specific color, just confirm the change (e.g., "Alright, changing it to green now!").
            * Keep responses friendly and concise.

        6.  **If Unsure:** If you can't determine a color from the request, politely ask the user to rephrase or try something else.
        """
            )
        }

        val changeColorFunction = FunctionDeclaration(
            "changeBackgroundColor",
            "Change the background color",
            mapOf("hexColor" to Schema.string("The hex color code (e.g., #FF5733) to change the color"))
        )

        @OptIn(PublicPreviewAPI::class)
        val generativeModel = Firebase.ai(backend = GenerativeBackend.vertexAI("us-central1")).generativeModel(
            modelName = "gemini-2.0-flash-live-preview-04-09",
            generationConfig = liveGenerationConfig,
            systemInstruction = systemInstruction,
            tools = listOf(Tool.functionDeclarations(listOf(changeColorFunction)))
        )

        session = generativeModel.connect()

        if (session != null) {
            println("Session connected successfully")
        }
    }

    fun handler(functionCall: FunctionCallPart): FunctionResponsePart {
        return when (functionCall.name) {
            "changeBackgroundColor" -> {
                val hexColor = functionCall.args["hexColor"]!!.jsonPrimitive.content
                changeBackgroundColor(hexColor)
                val response = JsonObject(
                    mapOf(
                        "success" to JsonPrimitive(true),
                        "message" to JsonPrimitive("Background color changed to $hexColor")
                    )
                )
                FunctionResponsePart(functionCall.name, response)
            }

            else -> {
                val response = JsonObject(
                    mapOf(
                        "error" to JsonPrimitive("Unknown function: ${functionCall.name}")
                    )
                )
                FunctionResponsePart(functionCall.name, response)
            }
        }
    }

    // Start the wave animation when listening
    private fun startWaveAnimation() {
        waveformView.visibility = View.VISIBLE
        waveformView.startAnimation()
        isListening = true
    }

    // Stop the wave animation
    private fun stopWaveAnimation() {
        waveformView.stopAnimation()
        isListening = false
    }

    // Material Design button state handling
    private fun setButtonStates(isListening: Boolean) {
        this.isListening = isListening

        if (isListening) {
            // Visual indication for Start button - disabled state with Material design
            startButton.isEnabled = false
            startButton.alpha = 0.6f

            // Enable Stop button
            stopButton.isEnabled = true
            stopButton.alpha = 1.0f
        } else {
            // Enable Start button
            startButton.isEnabled = true
            startButton.alpha = 1.0f

            // Visual indication for Stop button - disabled state with Material design
            stopButton.isEnabled = false
            stopButton.alpha = 0.6f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        setContentView(R.layout.activity_main)

        initializeViews()

        //  Ensure that the Firebase Live API session is fully initialized
        //  before the app continues with the rest of its setup
        runBlocking {
            liveAPISetup()
        }

        setupButtonListeners()

        // Initial color application
        colorCard.setCardBackgroundColor(Color.parseColor(currentColorHex))
        updateColorHexDisplay(currentColorHex)

        setButtonStates(false)
    }

    private fun initializeViews() {
        // Get references to our UI components
        startButton = findViewById(R.id.button)
        stopButton = findViewById(R.id.button2)
        colorCard = findViewById(R.id.colorCard)
        buttonsCard = findViewById(R.id.buttonsCard)
        currentColorText = findViewById(R.id.currentColorText)
        waveformView = findViewById(R.id.waveformView)
        rootView = findViewById(android.R.id.content)
    }

    private fun setupButtonListeners() {
        val scope = CoroutineScope(Dispatchers.IO)

        startButton.setOnClickListener {
            setButtonStates(true)
            waveformView.setColor(googleBlue)
            startWaveAnimation()

            scope.launch {
                //session?.stopReceiving()
                session?.startAudioConversation(::handler)
            }
        }

        stopButton.setOnClickListener {
            setButtonStates(false)
            stopWaveAnimation()
            scope.launch {
                session?.stopAudioConversation()
            }
        }
    }
}