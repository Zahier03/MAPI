package com.example.firstaiapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class PromptAIActivity : AppCompatActivity() {
    private lateinit var inputField: EditText
    private lateinit var submitButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var voiceInputButton: Button
    private lateinit var imageInputButton: Button
    private var selectedBitmap: Bitmap? = null
    private lateinit var textGenerated: TextView

    private val REQUEST_CODE_RECORD_AUDIO = 1
    private val REQUEST_CODE_SPEECH_INPUT = 2
    private val REQUEST_CODE_IMAGE_PICK = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prompt_ai_layout)

        inputField = findViewById(R.id.input_field)
        submitButton = findViewById(R.id.submit_button)
        progressBar = findViewById(R.id.progress_bar)
        voiceInputButton = findViewById(R.id.voice_input_button)
        imageInputButton = findViewById(R.id.image_input_button)
        textGenerated = findViewById(R.id.textGenerated)

        // Check and request microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_RECORD_AUDIO
            )
        }

        voiceInputButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeechToText()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required for speech recognition",
                    Toast.LENGTH_LONG
                ).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_CODE_RECORD_AUDIO
                )
            }
        }

        imageInputButton.setOnClickListener {
            openGalleryForImage()
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "YOUR_API_HERE"
        )

        generateSuggestedTopic(generativeModel)

        submitButton.setOnClickListener {
            val prompt = inputField.text.toString()
            if (prompt.isNotEmpty()) {
                generateMindMapFromText(prompt, generativeModel)
            } else {
                Toast.makeText(this, "Please enter a topic or select an image first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Sorry, your device doesn't support speech input",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("SpeechToText", "Error starting speech recognition: ${e.message}")
        }
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_SPEECH_INPUT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    result?.let {
                        if (it.isNotEmpty()) {
                            val recognizedText = it[0]
                            inputField.setText(recognizedText)
                            Log.d("SpeechToText", "Recognized text: $recognizedText")
                        }
                    }
                } else {
                    Log.d("SpeechToText", "Speech recognition failed or was cancelled")
                    Toast.makeText(
                        this,
                        "Speech recognition failed or was cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            REQUEST_CODE_IMAGE_PICK -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val imageUri = data.data
                    val imageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                    selectedBitmap = imageBitmap
                    recognizeTextFromImage(imageBitmap)
                } else {
                    Log.d("ImagePick", "Image selection failed or was cancelled")
                    Toast.makeText(
                        this,
                        "Image selection failed or was cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun recognizeTextFromImage(bitmap: Bitmap) {
        showLoading(true)
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text
                inputField.setText(recognizedText)
                Log.d("TextRecognition", "Recognized text: $recognizedText")
                showLoading(false)
                Toast.makeText(
                    this,
                    "Text recognized from image. You can now generate the mind map.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { e ->
                Log.e("TextRecognition", "Error recognizing text: ${e.message}")
                Toast.makeText(
                    this,
                    "Error recognizing text from image",
                    Toast.LENGTH_SHORT
                ).show()
                showLoading(false)
            }
    }

    private fun generateMindMapFromText(prompt: String, generativeModel: GenerativeModel) {
        showLoading(true)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val modifiedPrompt = """
                     Pretend that you're an excellent teacher creating a good mind map in XML for the topic, transfrom everything into given format based on student requirements or this keyword: "$prompt"
                    
                    Follow this template:
                    <mindmap>
                      <branch name="Main Topic">
                        <sub-branch name="Subtopic 1">
                          <explanation> only use explanation for explaining subtopic for example advantage and disadvantage 1</explanation>
                          <sub-sub-branch>Sub-subtopic 1.1</sub-sub-branch>
                          <explanation>use explanation for explaining sub-sub branch  for example advantage and disadvantage </explanation>
                          <sub-sub-branch>Sub-subtopic 1.2</sub-sub-branch>
                          <explanation>use explanation for explaining sub-sub branch  for example advantage and disadvantage </explanation>
                        </sub-branch>
                        <sub-branch name="Subtopic 2">
                          <explanation> only use explanation for explaining subtopic for example advantage and disadvantage 2</explanation>
                          <sub-sub-branch>Sub-subtopic 2.1</sub-sub-branch>
                          <explanation>use explanation for explaining sub-sub brach topic for example advantage and disadvantage </explanation>
                          <sub-sub-branch>Sub-subtopic 2.2</sub-sub-branch>
                          <explanation>use explanation for explaining sub-sub branch topic for example advantage and disadvantage </explanation>
                        </sub-branch>
                      </branch>
                      <!-- More branches as needed -->
                    </mindmap>
                    
                    Generate a comprehensive mind map XML for the given topic. If the topic is inappropriate or sensitive, respond with <mindmap><error>Topic is inappropriate or sensitive</error></mindmap>. Don't put any other word or symbols other than the set format. Please don't put this (xml),  force all words into the given XML format, give about minimum 100 words and maximum 300 words count. Include brief explanations for each sub-branch and add sub-sub-branches where appropriate.
                    """.trimIndent()

                val response = generativeModel.generateContent(modifiedPrompt)
                val mindMapXml = response.text ?: "<mindmap><error>Empty response</error></mindmap>"

                Log.d("MindMapXML", mindMapXml)

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    val intent = Intent(this@PromptAIActivity, GenerateMindMapActivity::class.java)
                    intent.putExtra("mindMapXml", mindMapXml)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("GenerateMindMap", "Error generating mind map: ${e.message}")
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@PromptAIActivity,
                        "Error generating mind map",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        val loadingOverlay = findViewById<FrameLayout>(R.id.loading_overlay)
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE

        inputField.isEnabled = !isLoading
        submitButton.isEnabled = !isLoading
        voiceInputButton.isEnabled = !isLoading
        imageInputButton.isEnabled = !isLoading
    }

    private fun generateSuggestedTopic(generativeModel: GenerativeModel) {
        showLoading(true)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val prompt = "don't put any symbol in generated words for example '*&^%$#@!', just give words but numbers are allow. Suggest a 5 good mind map topic for today randomly from education topic /n this is separate command, same no symbol: give what on news today just briefly" +
                        "follow this format : MIND MAP TOPIC FOR TODAY: 'topic name' /n 'topic name' /n 'topic name'" +
                        "ON NEWS TODAY (GLOBAL): 'briefly summary news'" +
                        "force generate word to follow format above"
                val response = generativeModel.generateContent(prompt)
                val suggestedTopic = response.text ?: "Error generating suggested topic"

                withContext(Dispatchers.Main) {
                    textGenerated.text = suggestedTopic
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.e("SuggestedTopic", "Error generating suggested topic: ${e.message}")
                withContext(Dispatchers.Main) {
                    textGenerated.text = "Error generating suggested topic"
                    showLoading(false)
                }
            }
        }
    }
}
