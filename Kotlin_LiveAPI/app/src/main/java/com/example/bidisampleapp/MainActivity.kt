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
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentLinkedQueue
import android.widget.TextView
import kotlinx.serialization.json.jsonPrimitive


@OptIn(PublicPreviewAPI::class)
class MainActivity : ComponentActivity() {
    var session: LiveSession? = null
    val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    val playBackQueue = ConcurrentLinkedQueue<ByteArray>()
    private lateinit var connectButton: MaterialButton
    private lateinit var startStopButton: MaterialButton
    private lateinit var rootView: View
    private lateinit var colorCard: MaterialCardView
    private lateinit var buttonsCard: MaterialCardView
    private lateinit var currentColorText: TextView
    private lateinit var waveformView: AudioWaveformView

    private val defaultBackgroundColor = "#1E293B" // Elegant deep blue
    private var currentColorHex = "#3B82F6" // Card default color - Material blue
    private var isListening = false
    private var isConnected = false

    // Material Design colors
    private val googleBlue = Color.parseColor("#4285F4")

    fun changeBackgroundColor(hexColor: String) {
        runOnUiThread {
            try {
                val color = Color.parseColor(hexColor)
                currentColorHex = hexColor

                // Set the card background color
                colorCard.setCardBackgroundColor(color)

                // Update color text
                updateColorHexDisplay(hexColor)

                // Update waveform color
                waveformView.setColor(color)

                // Short toast notification
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
        const val TAG = "MainActivity"

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

    fun startConversation() {
        AUDIO_TRACK.play()
        startRecording()
        while (!audioQueue.isEmpty()) {
            audioQueue.poll()?.let {
                CoroutineScope(Dispatchers.Default).launch {
                    session?.sendMediaStream(listOf(MediaData(it, "audio/pcm")))
                }
            }
        }
        CoroutineScope(Dispatchers.Default).launch {
            session!!.receive().collect {
                AUDIO_RECORD.stop()
                if (it.status == Status.TURN_COMPLETE) {
                    startRecording()
                } else {
                    val audioData = it.data?.parts?.get(0)?.asInlineDataPartOrNull()?.inlineData
                    if (audioData != null) {
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

    suspend fun bidiSetup(): Boolean {
        return try {
            val liveGenerationConfig = liveGenerationConfig {
                speechConfig = SpeechConfig(voice = Voices.UNSPECIFIED)
                responseModality = ResponseModality.AUDIO
            }

            val systemInstruction = content("user") {
                text(
                    "You are a helpful assistant that can show colors. " +
                            "When a user asks to change the color, identify the color name and convert it to a hex code. " +
                            "Then call the changeBackgroundColor function with the hex code. " +
                            "Be creative with colors - if the user asks for things like sunset, ocean, forest, etc., " +
                            "choose appropriate hex colors that match those themes. For example, sunset could be #FF7E5F, " +
                            "ocean could be #1A5276, forest could be #1E8449."
                )
            }

            val changeColorFunction = FunctionDeclaration(
                "changeBackgroundColor",
                "Change the color of the card",
                mapOf("hexColor" to Schema.string("The hex color code (e.g., #FF5733) to change the color to"))
            )

            @OptIn(PublicPreviewAPI::class)
            val generativeModel = FirebaseVertexAI.instance.liveModel(
                "gemini-2.0-flash-exp",
                generationConfig = liveGenerationConfig,
                systemInstruction = systemInstruction,
                tools = listOf(Tool.functionDeclarations(listOf(changeColorFunction)))
            )

            session = generativeModel.connect()
            Log.d(TAG, "Session connected successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            false
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

    // Toggle button states
    private fun setButtonStates(isListening: Boolean) {
        this.isListening = isListening

        if (isListening) {
            startStopButton.text = "Stop"
            startStopButton.icon = ResourcesCompat.getDrawable(resources, android.R.drawable.ic_media_pause, null)
        } else {
            startStopButton.text = "Start"
            startStopButton.icon = ResourcesCompat.getDrawable(resources, android.R.drawable.ic_media_play, null)
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
        setupButtonListeners()

        // Set initial card color
        colorCard.setCardBackgroundColor(Color.parseColor(currentColorHex))
        updateColorHexDisplay(currentColorHex)

        // Set root background color
        window.decorView.setBackgroundColor(Color.parseColor(defaultBackgroundColor))

        // Initial button states
        startStopButton.isEnabled = false
        startStopButton.alpha = 0.6f
    }

    private fun initializeViews() {
        // Get references to UI components
        connectButton = findViewById(R.id.connectButton)
        startStopButton = findViewById(R.id.startStopButton)
        colorCard = findViewById(R.id.colorCard)
        buttonsCard = findViewById(R.id.buttonsCard)
        currentColorText = findViewById(R.id.currentColorText)
        waveformView = findViewById(R.id.waveformView)
        rootView = findViewById(android.R.id.content)
    }

    private fun setupButtonListeners() {
        val scope = CoroutineScope(Dispatchers.IO)

        connectButton.setOnClickListener {
            // Disable connect button during connection attempt
            connectButton.isEnabled = false
            connectButton.alpha = 0.6f

            scope.launch {
                val connected = bidiSetup()

                withContext(Dispatchers.Main) {
                    if (connected) {
                        // Connection successful
                        isConnected = true

                        // Disable connect button (already connected)
                        connectButton.isEnabled = false
                        connectButton.alpha = 0.6f

                        // Enable the Start button
                        startStopButton.isEnabled = true
                        startStopButton.alpha = 1.0f

                        // Visual confirmation
                        Toast.makeText(applicationContext, "Connected successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        // Connection failed
                        connectButton.isEnabled = true
                        connectButton.alpha = 1.0f

                        Toast.makeText(applicationContext, "Connection failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        startStopButton.setOnClickListener {
            if (isListening) {
                // Stop functionality
                setButtonStates(false)
                stopWaveAnimation()

                scope.launch {
                    try {
                        session?.stopAudioConversation()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping conversation: ${e.message}", e)
                    }
                }
            } else {
                // Start functionality
                setButtonStates(true)
                waveformView.setColor(googleBlue)
                startWaveAnimation()

                scope.launch {
                    try {
                        session?.let {
                            it.stopReceiving()
                            it.startAudioConversation(::handler)
                        } ?: run {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Session unavailable", Toast.LENGTH_SHORT).show()
                                stopWaveAnimation()
                                setButtonStates(false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting conversation: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Error starting conversation", Toast.LENGTH_SHORT).show()
                            stopWaveAnimation()
                            setButtonStates(false)
                        }
                    }
                }
            }
        }
    }
}