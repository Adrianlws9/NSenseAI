package com.example.nsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nsense.data.local.NoiseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in: skip the login screen
        if (SessionManager.isLoggedIn(this)) {
            openHome()
            return
        }

        setContentView(R.layout.activity_login)

        val database = NoiseDatabase.getDatabase(this)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoToRegister = findViewById<Button>(R.id.btnGoToRegister)

        btnLogin.setOnClickListener {

            val email = etEmail.text.toString().trim().lowercase()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false

            lifecycleScope.launch {

                // Look the user up and check the password hash (off the main thread)
                val user = withContext(Dispatchers.IO) {
                    val found = database.userDao().getUserByEmail(email)
                    if (found != null &&
                        PasswordUtils.verify(password, found.salt, found.passwordHash)
                    ) {
                        found
                    } else {
                        null
                    }
                }

                if (user != null) {
                    SessionManager.login(this@LoginActivity, user.id, user.name)
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                    openHome()
                } else {
                    btnLogin.isEnabled = true
                    Toast.makeText(
                        this@LoginActivity,
                        "Invalid email or password",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun openHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}