package com.example.nsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nsense.data.local.NoiseDatabase
import com.example.nsense.data.local.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_register)

        val database = NoiseDatabase.getDatabase(this)

        val etName =
            findViewById<EditText>(R.id.etName)

        val etEmail =
            findViewById<EditText>(R.id.etEmail)

        val etPassword =
            findViewById<EditText>(R.id.etPassword)

        val etConfirmPassword =
            findViewById<EditText>(R.id.etConfirmPassword)

        val btnRegister =
            findViewById<Button>(R.id.btnRegister)

        val btnGoToLogin =
            findViewById<Button>(R.id.btnGoToLogin)

        btnRegister.setOnClickListener {

            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (name.isEmpty() ||
                email.isEmpty() ||
                password.isEmpty() ||
                confirmPassword.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "Please fill all fields",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (password != confirmPassword) {

                Toast.makeText(
                    this,
                    "Passwords do not match",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val user = User(
                name = name,
                email = email,
                password = password
            )

            CoroutineScope(Dispatchers.IO).launch {

                database.userDao().insertUser(user)

                runOnUiThread {

                    Toast.makeText(
                        this@RegisterActivity,
                        "Registration successful",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnGoToLogin.setOnClickListener {

            val intent =
                Intent(this, LoginActivity::class.java)

            startActivity(intent)
        }
    }
}