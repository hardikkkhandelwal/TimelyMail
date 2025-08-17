package com.example.timelymail

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.api.services.gmail.GmailScopes
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch


class google_sign_in : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var googleSignInClient: GoogleSignInClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.sleep(1000)
        installSplashScreen()
        setContentView(R.layout.activity_google_sign_in)

        val progressBar1 = findViewById<ProgressBar>(R.id.progressBar1)
        val progressBar2 = findViewById<ProgressBar>(R.id.progressBar2)

        progressBar1.max = 1000
        progressBar2.max = 1000

        val currentProgress = 1000

        fun startProgressAnimation() {
            progressBar1.progress = 0
            progressBar2.progress = 0

            val anim1 = ObjectAnimator.ofInt(progressBar1, "progress", currentProgress)
            anim1.duration = 5000

            val anim2 = ObjectAnimator.ofInt(progressBar2, "progress", currentProgress)
            anim2.duration = 5000

            anim1.doOnEnd {
                anim2.start()
            }

            anim2.doOnEnd {
                // Restart the whole cycle
                startProgressAnimation()
            }

            anim1.start()
        }

        startProgressAnimation()


        // Initialize Firebase Auth
        auth = Firebase.auth

        // Initialize Credential Manager
        credentialManager = CredentialManager.create(this)

        // Google ID Option
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.clientid))
            .setFilterByAuthorizedAccounts(false)
            .build()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly")) // Required
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)




        // Credential Request
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()


        val btnCreateGoogleAC = findViewById<Button>(R.id.btnCreateGoogleAC)
        btnCreateGoogleAC.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://accounts.google.com/signup")
            startActivity(intent)
        }

        // Button click listener — INSIDE onCreate
        val signInButton = findViewById<Button>(R.id.btnGoogleSignIn)
        signInButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // This line ensures previous account is signed out so chooser appears again
                    googleSignInClient.signOut().addOnCompleteListener {
                        val signInIntent = googleSignInClient.signInIntent
                        startActivityForResult(signInIntent, 1001)
                    }
                } catch (e: Exception) {
                    Log.e("TAG", "Sign-in failed: ${e.localizedMessage}")
                }
            }
        }

    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("TAG", "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user
                    Log.w("TAG", "signInWithCredential:failure", task.exception)
                    updateUI(null)
                }
            }
    }

    private fun signOut() {
        // Firebase sign out
        auth.signOut()

        // When a user signs out, clear the current user credential state from all credential providers.
        lifecycleScope.launch {
            try {
                val clearRequest = ClearCredentialStateRequest()
                credentialManager.clearCredentialState(clearRequest)
                updateUI(null)
            } catch (e: ClearCredentialException) {
                Log.e("TAG", "Couldn't clear user credentials: ${e.localizedMessage}")
            }
        }
    }



    private fun handleSignIn(credential: Credential) {
        // Check if credential is of type Google ID
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            // Create Google ID Token
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            // Sign in to Firebase with using the token
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w("TAG", "Credential is not of type Google ID!")
        }
    }
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) lifecycleScope.launch {
            val token = getAccessToken()
            val intent = Intent(this@google_sign_in, ProfileActivity::class.java)
            intent.putExtra("email", user.email)
            intent.putExtra("token", token)
            startActivity(intent)
            finish()
        }
        else {
            Log.d("TAG", "User not logged in.")
        }
    }

    private suspend fun getAccessToken(): String? {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this)
        return try {
            account?.let {
                val accountName = it.email ?: it.account?.name
                if (accountName != null) {
                    com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        this,
                        accountName,
                        "oauth2:${GmailScopes.GMAIL_READONLY}"
                    )
                } else {
                    Log.e("TAG", "No valid account name found.")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("TAG", "Error getting access token: ${e.message}")
            null
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                Log.e("TAG", "Google Sign In failed", task.exception)
            }
        }
    }

}