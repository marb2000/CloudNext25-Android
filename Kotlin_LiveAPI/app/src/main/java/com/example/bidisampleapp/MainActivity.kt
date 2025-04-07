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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
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
    private lateinit var conversationCard: CardView

    private val defaultBackgroundColor = "#7953D2"

    fun changeBackgroundColor(hexColor: String) {
        runOnUiThread {
            try {
                val color = Color.parseColor(hexColor)

                rootView = findViewById(android.R.id.content)
                rootView.setBackgroundColor(color)

                updateStatus(
                    "Background changed to $hexColor",
                    android.R.drawable.ic_dialog_info,
                    Color.WHITE
                )
                Toast.makeText(this, "Background color changed to $hexColor", Toast.LENGTH_SHORT).show()
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

    // Helper to update status display
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
            text("You are a helpful assistant that can change the app's background color. " +
                    "When a user asks to change the color, identify the color name and convert it to a hex code. " +
                    "Then call the changeBackgroundColor function with the hex code. " +
                    "Be creative with colors - if the user asks for things like sunset, ocean, forest, etc., " +
                    "choose appropriate hex colors that match those themes.")
        }

        val changeColorFunction = FunctionDeclaration(
            "changeBackgroundColor",
            "Change the background color of the app",
            mapOf("hexColor" to Schema.string("The hex color code (e.g., #FF5733) to change the background to"))
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
            println("Session is good")
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
                FunctionResponsePart(functionCall.name ?: "unknown", response)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        setContentView(R.layout.activity_main)

        initializeViews()

        runBlocking {
            bidiSetup()
        }

        setupButtonListeners()

        initializeConversationListener()
    }

    private fun initializeViews() {
        // Get references to our UI components
        startButton = findViewById(R.id.button)
        stopButton = findViewById(R.id.button2)
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        conversationText = findViewById(R.id.textView2)
        conversationCard = findViewById(R.id.conversationCard)
        rootView = findViewById(android.R.id.content)
    }

    private fun setupButtonListeners() {
        val scope = CoroutineScope(Dispatchers.IO)

        startButton.setOnClickListener {

            startButton.isEnabled = false
            stopButton.isEnabled = true

            // Update status UI
            updateStatus(
                "Listening...",
                android.R.drawable.ic_btn_speak_now,
                Color.parseColor("#4285F4")
            )

            scope.launch {
                session?.stopReceiving()
                session?.startAudioConversation(::handler)
            }
        }

        stopButton.setOnClickListener {
            // Visual feedback for button press
            stopButton.isEnabled = false
            startButton.isEnabled = true

            // Update status UI
            updateStatus(
                "Conversation stopped. Press Start to begin again.",
                android.R.drawable.ic_media_pause,
                Color.parseColor("#757575")
            )

            scope.launch {
                session?.stopAudioConversation()
            }
        }
    }

    private fun initializeConversationListener() {
        // Default state of buttons
        stopButton.isEnabled = false

        CoroutineScope(Dispatchers.Default).launch {
            var text = ""

            session?.receive()?.collect {
                if (it.status == Status.TURN_COMPLETE) {
                    runOnUiThread {
                        // Append new conversation text
                        val currentText = conversationText.text.toString()
                        if (currentText.contains("Conversation will appear here...")) {
                            conversationText.text = text
                        } else {
                            conversationText.text = "$currentText\n\n$text"
                        }
                    }
                    text = ""
                } else if (it.status == Status.NORMAL) {
                    if (!it.functionCalls.isNullOrEmpty()) {
                        println("Function call received: ${it.functionCalls}")
                    } else {
                        text += it.text
                    }
                }
            }
        }
    }
}