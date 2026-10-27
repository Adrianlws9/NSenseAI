package com.example.nsense

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nsense.data.local.NoiseDatabase
import com.example.nsense.data.local.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_register)

        val database = NoiseDatabase.getDatabase(this)

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoToLogin = findViewById<Button>(R.id.btnGoToLogin)

        btnRegister.setOnClickListener {

            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim().lowercase()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(
                    this,
                    "Password must be at least 6 characters",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false

            lifecycleScope.launch {

                val created = withContext(Dispatchers.IO) {
                    val dao = database.userDao()
                    if (dao.getUserByEmail(email) != null) {
                        false
                    } else {
                        val salt = PasswordUtils.newSalt()
                        dao.insertUser(
                            User(
                                name = name,
                                email = email,
                                passwordHash = PasswordUtils.hash(password, salt),
                                salt = salt
                            )
                        )
                        true
                    }
                }

                if (created) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Registration successful. Please log in.",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish() // back to the login screen
                } else {
                    btnRegister.isEnabled = true
                    Toast.makeText(
                        this@RegisterActivity,
                        "An account with this email already exists",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnGoToLogin.setOnClickListener {
            finish()
        }
    }
}