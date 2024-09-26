package com.example.firstaiapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val DescriptionButton = findViewById<Button>(R.id.button_introduction)
        DescriptionButton.setOnClickListener {
            val intent = Intent(this, DescriptionProject::class.java)
            startActivity(intent)
        }
        val promptButton = findViewById<Button>(R.id.button_prompt_ai)
        promptButton.setOnClickListener {
            val intent = Intent(this, PromptAIActivity::class.java)
            startActivity(intent)
        }
        // Display the current date
        val textViewDate = findViewById<TextView>(R.id.textViewDate)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        textViewDate.text = currentDate
    }
}