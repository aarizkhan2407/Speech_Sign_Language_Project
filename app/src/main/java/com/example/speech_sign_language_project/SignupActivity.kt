package com.example.speech_sign_language_project

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.signupEmail)
        val password = findViewById<EditText>(R.id.signupPassword)
        val signupButton = findViewById<Button>(R.id.signupButton)
        val backButton = findViewById<Button>(R.id.backToLoginButton)

        signupButton.setOnClickListener {

            val emailText = email.text.toString().trim()
            val passText = password.text.toString().trim()

            if (emailText.isEmpty() || passText.isEmpty()) {

                Toast.makeText(
                    this,
                    "Please enter email and password",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(emailText, passText)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        val user = auth.currentUser

                        user?.sendEmailVerification()
                            ?.addOnCompleteListener {

                                Toast.makeText(
                                    this,
                                    "Signup successful. Check your email to verify, then login.",
                                    Toast.LENGTH_LONG
                                ).show()

                            }

                    } else {

                        Toast.makeText(
                            this,
                            task.exception?.message ?: "Signup failed",
                            Toast.LENGTH_LONG
                        ).show()

                    }
                }
        }

        // Back button returns to LoginActivity
        backButton.setOnClickListener {

            finish()

        }
    }
}