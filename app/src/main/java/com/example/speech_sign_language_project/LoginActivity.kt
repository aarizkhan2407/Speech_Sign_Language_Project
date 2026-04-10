package com.example.speech_sign_language_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        val loginButton = findViewById<Button>(R.id.loginButton)
        val signupButton = findViewById<Button>(R.id.signupButton)
        val googleLoginButton = findViewById<Button>(R.id.googleLoginButton)

        // ✅ GOOGLE SIGN-IN CONFIG (CORRECT)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // ✅ CORRECT LINE
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // ---------------- EMAIL LOGIN ----------------

        loginButton.setOnClickListener {

            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        val user = auth.currentUser

                        if (user != null && user.isEmailVerified) {

                            Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this, MainActivity::class.java))
                            finish()

                        } else {

                            Toast.makeText(
                                this,
                                "Please verify your email before logging in.",
                                Toast.LENGTH_LONG
                            ).show()

                            auth.signOut()
                        }

                    } else {

                        Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show()

                    }
                }
        }

        // ---------------- SIGNUP BUTTON ----------------

        signupButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // ---------------- GOOGLE LOGIN ----------------

        googleLoginButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    // ---------------- GOOGLE RESULT ----------------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)

                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnCompleteListener {

                        if (it.isSuccessful) {

                            Toast.makeText(this, "Google Login Successful", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this, MainActivity::class.java))
                            finish()

                        } else {

                            Toast.makeText(this, "Google Login Failed", Toast.LENGTH_SHORT).show()

                        }
                    }

            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In Error", Toast.LENGTH_SHORT).show()
            }
        }
    }
}