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
import android.widget.ImageView
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


@OptIn(PublicPreviewAPI::class)
class MainActivity : ComponentActivity() {
    var session: LiveSession? = null
    val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    val playBackQueue = ConcurrentLinkedQueue<ByteArray>()
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var conversationText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var rootView: View
    private lateinit var colorCard: MaterialCardView
    private lateinit var buttonsCard: MaterialCardView
    private lateinit var statusCard: MaterialCardView
    private lateinit var currentColorText: TextView
    private lateinit var waveformView: AudioWaveformView

    private val defaultBackgroundColor = "#7953D2"
    private var currentColorHex = defaultBackgroundColor
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

                rootView = findViewById(android.R.id.content)
                rootView.setBackgroundColor(color)

                updateColorHexDisplay(hexColor)

                waveformView.setColor(color)

                updateStatus(
                    "Color changed to $hexColor",
                    android.R.drawable.ic_dialog_info,
                    Color.WHITE
                )

                // Material design toast with proper duration
                Toast.makeText(this, "Color changed to $hexColor", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                updateStatus(
                    "Invalid color code: $hexColor",
                    android.R.drawable.ic_dialog_alert,
                    Color.WHITE
                )
                Toast.makeText(this, "Invalid color code: $hexColor", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Enhanced method to update the color hex display with better contrast
    private fun updateColorHexDisplay(hexColor: String) {
        currentColorText.text = "Current Color: $hexColor"

        // Ensure text always has good contrast with background
        val color = Color.parseColor(hexColor)
        val brightness =
            (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000

        // Keep background semi-transparent black for consistency
        currentColorText.setBackgroundColor(Color.parseColor("#99000000"))

        // Always use white text for better visibility against dark background
        currentColorText.setTextColor(Color.WHITE)
    }

    private fun updateStatus(message: String, iconResId: Int, textColor: Int) {
        statusText.text = message
        statusText.setTextColor(textColor)
        statusIcon.setImageResource(iconResId)
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

    suspend fun bidiSetup() {
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
        if (isListening) {
            // Visual indication for Start button - disabled state with Material design
            startButton.isEnabled = false
            startButton.alpha = 0.6f  // Proper material disabled alpha

            // Enable Stop button
            stopButton.isEnabled = true
            stopButton.alpha = 1.0f
            stopButton.strokeWidth = 2  // Make outline more visible
        } else {
            // Enable Start button
            startButton.isEnabled = true
            startButton.alpha = 1.0f

            // Visual indication for Stop button - disabled state with Material design
            stopButton.isEnabled = false
            stopButton.alpha = 0.6f  // Proper material disabled alpha
            stopButton.strokeWidth = 1  // Subtle outline when disabled
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

        runBlocking {
            bidiSetup()
        }

        setupButtonListeners()

        // Initial color application
        changeBackgroundColor(defaultBackgroundColor)

        // Initial button states
        setButtonStates(false)
    }

    private fun initializeViews() {
        // Get references to our UI components
        startButton = findViewById(R.id.button)
        stopButton = findViewById(R.id.button2)
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        conversationText = findViewById(R.id.textView2)
        colorCard = findViewById(R.id.colorCard)
        buttonsCard = findViewById(R.id.buttonsCard)
        statusCard = findViewById(R.id.statusCard)
        currentColorText = findViewById(R.id.currentColorText)
        waveformView = findViewById(R.id.waveformView)
        rootView = findViewById(android.R.id.content)

        // Apply elevation to status elements for Material Design depth
        currentColorText.elevation = 8f
    }

    private fun setupButtonListeners() {
        val scope = CoroutineScope(Dispatchers.IO)

        startButton.setOnClickListener {
            // Update button states using Material Design guidelines
            setButtonStates(true)

            // Update status UI and start wave animation with Google blue
            updateStatus(
                "Listening...",
                android.R.drawable.ic_btn_speak_now,
                googleBlue
            )

            // Set waveform color to match Google blue for consistency
            waveformView.setColor(googleBlue)
            startWaveAnimation()

            scope.launch {
                session?.stopReceiving()
                session?.startAudioConversation(::handler)
            }
        }

        stopButton.setOnClickListener {
            // Update button states using Material Design guidelines
            setButtonStates(false)

            // Update status UI and stop wave animation
            updateStatus(
                "Conversation stopped. Press Start to begin again.",
                android.R.drawable.ic_media_pause,
                materialGrey
            )
            stopWaveAnimation()

            scope.launch {
                session?.stopAudioConversation()
            }
        }
    }
}