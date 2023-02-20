package com.example.flipviewtest

import android.content.ClipData
import android.content.ClipData.Item
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.flipviewtest.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val foldableListLayout = binding.foldableList
        foldableListLayout.setAdapter(NormalAdapter(this, arrayListOf(ClipData.Item("12"), Item("12121"))))
    }

}
