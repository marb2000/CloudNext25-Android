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
import com.google.firebase.vertexai.FirebaseVertexAI
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

// The app doesn't try to listen while the AI is talking
// When the AI finishes its turn, the microphone automatically turns back on
// The conversation flows naturally without requiring button presses for each turn
//
// It's very much like a tennis match where the ball (speaking turn) goes back and forth,
// with the app managing the transitions so you don't have to manually indicate whose turn it is each time.

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

                // If you want to change the waveform color, uncomment the following line:
                // waveformView.setColor(color)

                Toast.makeText(this, "Color updated", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, "Invalid color code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateColorHexDisplay(hexColor: String) {
        currentColorText.text = "Color: $hexColor"
    }

    private companion object {
        val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val AUDIO_RECORD = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE
        )
        val AUDIO_TRACK =
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                24000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(
                    24000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ), AudioTrack.MODE_STREAM
            )
    }

    fun startRecording() {
        AUDIO_RECORD.startRecording()
        val buffer = ByteArray(BUFFER_SIZE)
        while (AUDIO_RECORD.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val bytesRead = AUDIO_RECORD.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                audioQueue.add(buffer.copyOf(bytesRead))
            }
        }
    }



    suspend fun liveAPISetup() {
        val liveGenerationConfig = liveGenerationConfig {
            speechConfig = SpeechConfig(voice = Voices.UNSPECIFIED)
            responseModality = ResponseModality.AUDIO
        }

        val systemInstruction = content("user") {
            text(
                """
        **Your Role:** You are a friendly and helpful voice assistant in this app. Your main job is to change the app's color based on user requests.

        **Interaction Steps:**

        1.  **Greeting (First turn ONLY):** Start the very first interaction with a friendly greeting like: "Hi! I'm your AI color assistant, ready to help. What color would you like to see?" (Adapt naturally, don't repeat this exact phrase every time).

        2.  **Understand Request:** Listen for the user asking for a specific color (e.g., "blue", "emerald green") or an abstract theme/concept (e.g., "ocean", "happiness", "sunset").

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
            "Change the color of the card",
            mapOf("hexColor" to Schema.string("The hex color code (e.g., #FF5733) to change the color to"))
        )

        // LIVEAPI STEP 1 - Initialize the Live API session
        // Get the instance of the service
        @OptIn(PublicPreviewAPI::class)
        val generativeModel = FirebaseVertexAI.instance.liveModel(
            "gemini-2.0-flash-exp",
            generationConfig = liveGenerationConfig,
            systemInstruction = systemInstruction,
            tools = listOf(Tool.functionDeclarations(listOf(changeColorFunction)))
        )

        // LIVEAPI STEP 2 - Create a running session
        // This is handles all the websocket connection
        session = generativeModel.connect()

        if (session != null) {
            println("Session connected successfully")
        }
    }

    fun handler(functionCall: FunctionCallPart): FunctionResponsePart {
        return when (functionCall.name) {
            "changeBackgroundColor" -> {
                val hexColor = functionCall.args!!["hexColor"]!!.jsonPrimitive.content
                changeBackgroundColor(hexColor)
                val response = JsonObject(
                    mapOf(
                        "success" to JsonPrimitive(true),
                        "message" to JsonPrimitive("Background color changed to $hexColor")
                    )
                )
                FunctionResponsePart("changeBackgroundColor", response)
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
            // LIVEAPI: Initialize the Live API session
            liveAPISetup()
        }

        setupButtonListeners()

        // Initial color application
        colorCard.setCardBackgroundColor(Color.parseColor(currentColorHex))
        updateColorHexDisplay(currentColorHex)

        // Set root background color
        //window.decorView.setBackgroundColor(Color.parseColor(defaultBackgroundColor))

        // Initial button states
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

            //LIVEAPI STEP 3: Launch a coroutine to handle the background communication
            scope.launch {
                // Stop any ongoing receiving operations
                session?.stopReceiving()

                // Start a new audio conversation with the model in the Start Button
                // The "::handler" passes your handler function as a callback
                // for processing Function Calls Tool
                session?.startAudioConversation(::handler)
            }
        }

        stopButton.setOnClickListener {
            // Update button states using Material Design guidelines
            setButtonStates(false)

            stopWaveAnimation()

            //LIVEAPI STEP 4: Stop the audio conversation on the Stop Button
            scope.launch {
                session?.stopAudioConversation()
            }
        }
    }
    fun startConversation() {
        AUDIO_TRACK.play()
        startRecording()

        // LIVEAPI STEP 5 -
        // We have two queues: User audio data and AI audio data
        // Send any existing User's audio data  in the queue to the model

        while (!audioQueue.isEmpty()) {
            audioQueue.poll()?.let {
                CoroutineScope(Dispatchers.Default).launch {
                    session?.sendMediaStream(listOf(MediaData(it, "audio/pcm")))
                }
            }
        }

        // LIVEAPI STEP 6 - Listen for responses from the AI in a background thread
        CoroutineScope(Dispatchers.Default).launch {
            session!!.receive().collect {
                // Stop recording while processing the response
                AUDIO_RECORD.stop()
                if (it.status == Status.TURN_COMPLETE) {
                    // If the AI's turn is complete, start recording again,
                    // The model will send a message that it's turn is comopleted
                    startRecording()
                } else {
                    // Otherwise, process audio data from the AI's response
                    val audioData = it.data?.parts?.get(0)?.asInlineDataPartOrNull()?.inlineData
                    if (audioData != null) {
                        // We play what the AI said
                        playBackQueue.add(audioData)
                    }
                }
            }
        }

        while (!playBackQueue.isEmpty()) {
            playBackQueue.poll()?.let {
                AUDIO_TRACK.write(it, 0, it.size)
            }
        }
    }
}