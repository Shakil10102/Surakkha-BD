package com.surakkha.bd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.surakkha.bd.network.LoginRequest
import com.surakkha.bd.network.NetworkResult
import com.surakkha.bd.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Check if user is already logged in (Token in SharedPreferences)
        val sharedPreferences = getSharedPreferences("surakkha_prefs", Context.MODE_PRIVATE)
        val existingToken = sharedPreferences.getString("auth_token", null)

        if (!existingToken.isNullOrEmpty()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // 2. Set layout view
        setContentView(R.layout.activity_login)

        // 3. Find views
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)

        // 4. Navigate to RegisterActivity
        tvRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 5. Handle Login Click
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Please enter your email"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Please enter your password"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            // Show loading UI
            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            // Perform network request on lifecycleScope
            lifecycleScope.launch {
                val request = LoginRequest(email = email, password = password)
                val result = RetrofitClient.safeApiCall {
                    RetrofitClient.apiService.login(request)
                }

                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true

                when (result) {
                    is NetworkResult.Success -> {
                        val token = result.data.token
                        val user = result.data.user

                        // Save JWT token and user info into SharedPreferences
                        sharedPreferences.edit()
                            .putString("auth_token", token)
                            .putString("user_name", user?.name ?: "")
                            .putString("user_email", user?.email ?: "")
                            .apply()

                        Toast.makeText(
                            this@LoginActivity,
                            result.data.message.ifEmpty { "Login successful!" },
                            Toast.LENGTH_SHORT
                        ).show()

                        // Navigate to MainActivity
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }

                    is NetworkResult.Error -> {
                        Toast.makeText(
                            this@LoginActivity,
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
