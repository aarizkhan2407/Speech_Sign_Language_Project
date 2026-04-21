package com.example.speech_sign_language_project

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AuthViewModel : ViewModel() {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun isUserAuthenticated(): Boolean {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val isGoogleUser = currentUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            return currentUser.isEmailVerified || isGoogleUser
        }
        return false
    }

    fun signOut() {
        auth.signOut()
    }
}
