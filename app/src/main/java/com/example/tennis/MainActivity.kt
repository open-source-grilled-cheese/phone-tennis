package com.example.tennis


import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "testing...")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val t: TextView = findViewById(R.id.words)
        t.text = "test"
    }

}