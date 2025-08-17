package com.example.timelymail

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class google_sign_in : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.sleep(1000)
        installSplashScreen()
        setContentView(R.layout.activity_google_sign_in)

        auth = FirebaseAuth.getInstance()

        // Setup progress bars
        val progressBar1 = findViewById<ProgressBar>(R.id.progressBar1)
        val progressBar2 = findViewById<ProgressBar>(R.id.progressBar2)
        progressBar1.max = 1000
        progressBar2.max = 1000
        startProgressAnimation(progressBar1, progressBar2)

        // Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnCreateGoogleAC = findViewById<Button>(R.id.btnCreateGoogleAC)
        btnCreateGoogleAC.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://accounts.google.com/signup")
            startActivity(intent)
        }

        val signInButton = findViewById<Button>(R.id.btnGoogleSignIn)
        signInButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    googleSignInClient.signOut().addOnCompleteListener {
                        startActivityForResult(googleSignInClient.signInIntent, 1001)
                    }
                } catch (e: Exception) {
                    Log.e("TAG", "Sign-in failed: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun startProgressAnimation(pb1: ProgressBar, pb2: ProgressBar) {
        val currentProgress = 1000
        pb1.progress = 0
        pb2.progress = 0

        val anim1 = ObjectAnimator.ofInt(pb1, "progress", currentProgress)
        anim1.duration = 5000
        val anim2 = ObjectAnimator.ofInt(pb2, "progress", currentProgress)
        anim2.duration = 5000

        anim1.doOnEnd { anim2.start() }
        anim2.doOnEnd { startProgressAnimation(pb1, pb2) }
        anim1.start()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            handleSignedInUser(currentUser)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) handleSignedInUser(user)
                } else {
                    Log.e("TAG", "signInWithCredential:failure", task.exception)
                }
            }
    }

    private fun handleSignedInUser(user: FirebaseUser) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            val email = account.email ?: account.account?.name ?: return
            val photoUrl = account.photoUrl

            // Add account to AccountManager
            val signedInAccount = SignedInAccount(
                email = email,
                photoUrl = photoUrl,
                firebaseUser = user,
                googleAccount = account
            )

            if (AccountManager.signedInAccounts.none { it.email == email }) {
                AccountManager.signedInAccounts.add(signedInAccount)
            }
            AccountManager.currentAccount = signedInAccount

            saveAccountsToPrefs()

            // Fetch token
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val accessToken = GoogleAuthUtil.getToken(
                        this@google_sign_in,
                        email,
                        "oauth2:${GmailScopes.GMAIL_READONLY}"
                    )
                    Log.d("AccessToken", accessToken)
                } catch (e: Exception) {
                    Log.e("TokenError", "Failed to get token", e)
                }
            }

            // Move to ProfileActivity with all data
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun saveAccountsToPrefs() {
        val prefs = getSharedPreferences("accounts", MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = com.google.gson.Gson()
        val json = gson.toJson(AccountManager.signedInAccounts.map {
            mapOf(
                "email" to it.email,
                "photoUrl" to it.photoUrl?.toString()
            )
        })
        editor.putString("accounts_list", json)
        editor.apply()
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
