package com.surakkha.bd

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.surakkha.bd.network.NetworkResult
import com.surakkha.bd.network.RegisterRequest
import com.surakkha.bd.network.RetrofitClient
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Find views
        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        // Navigate back to LoginActivity
        tvLoginLink.setOnClickListener {
            finish()
        }

        // Handle Registration Click
        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim().ifEmpty { null }
            val password = etPassword.text.toString().trim()

            // Basic validation
            if (name.isEmpty()) {
                etName.error = "Please enter your name"
                etName.requestFocus()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                etEmail.error = "Please enter your email"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Please enter a password"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                etPassword.error = "Password should be at least 6 characters"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            // Show loading UI
            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            // Perform registration network call
            lifecycleScope.launch {
                val request = RegisterRequest(
                    name = name,
                    email = email,
                    phone = phone,
                    password = password
                )

                val result = RetrofitClient.safeApiCall {
                    RetrofitClient.apiService.register(request)
                }

                progressBar.visibility = View.GONE
                btnRegister.isEnabled = true

                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(
                            this@RegisterActivity,
                            result.data.message.ifEmpty { "Registration successful! Please login." },
                            Toast.LENGTH_LONG
                        ).show()

                        // Return back to LoginActivity
                        finish()
                    }

                    is NetworkResult.Error -> {
                        Toast.makeText(
                            this@RegisterActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is NetworkResult.Loading -> {
                        // Handled by visibility change
                    }
                }
            }
        }
    }
}
