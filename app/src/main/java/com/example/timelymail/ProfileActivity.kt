package com.example.timelymail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ProfileActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_activty)

        auth = FirebaseAuth.getInstance()

        val emailTextView = findViewById<TextView>(R.id.emailTextView)
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val googleIcon = findViewById<ImageView>(R.id.googleIcon)
        val notifyIcon = findViewById<ImageButton>(R.id.notifyIcon)
        val searchIcon = findViewById<ImageButton>(R.id.searchIcon)



        //USER PROFILE PICTURE

        val user1 = FirebaseAuth.getInstance().currentUser
        val photoUrl = user1?.photoUrl

        val profileImageView = findViewById<ImageView>(R.id.profileImageView)

        photoUrl?.let {
            Glide.with(this)
                .load(it)
                .circleCrop()
                .into(profileImageView)
        }

        val addAccountIcon = findViewById<ImageView>(R.id.addAccountIcon)
        addAccountIcon.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(GmailScopes.GMAIL_SEND), Scope(GmailScopes.GMAIL_READONLY))
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, 2002)
            }
        }

        loadAccountsFromPrefs()

// Open drawer when Google icon is clicked
        googleIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

// Handle search icon click
        searchIcon.setOnClickListener {
            // TODO: Implement search functionality
            Toast.makeText(this, "Search clicked", Toast.LENGTH_SHORT).show()
        }

// Handle notification icon click
        notifyIcon.setOnClickListener {
            // TODO: Show notifications
            Toast.makeText(this, "Notifications clicked", Toast.LENGTH_SHORT).show()
        }


        // Display email
        val user = auth.currentUser
        if (user != null) {
            emailTextView.text = "Welcome: ${user.email}"
        }

        // Logout from button
        logoutButton.setOnClickListener {
            logout()
        }

        profileImageView.setOnClickListener {
            showAccountPopup(it)
        }



        // Sidebar item selection
//        navView.setNavigationItemSelectedListener {
//            when (it.itemId) {
//                R.id.nav_profile -> { /* already here */ }
//                R.id.nav_logout -> logout()
//                R.id.nav_gmail -> {
//                    // TODO: Add Gmail-related activity or fragment
//                }
//            }
//            true
//        }


        // Bottom navigation
        // Load default fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, InboxFragment())
            .commit()

        bottomNav.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_mail -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, InboxFragment())
                        .commit()
                    true
                }

                R.id.nav_calendar -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, CalendarFragment())
                        .commit()
                    true
                }

                else -> false
            }
        }

        val composeFab = findViewById<FloatingActionButton>(R.id.composeFab)
        composeFab.setOnClickListener {
            val intent = Intent(this, ComposeEmailActivity::class.java)
            intent.putExtra("from_email", AccountManager.currentAccount?.email)
            startActivityForResult(intent, 3001)
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

        // 1️⃣ Handle ComposeEmailActivity result
        if (requestCode == 3001 && resultCode == RESULT_OK) {
            // Email sent successfully, refresh inbox and badges
            val inboxFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? InboxFragment
            inboxFragment?.fetchEmails() // Refresh emails and badge counts
            Toast.makeText(this, "Inbox updated after sending email", Toast.LENGTH_SHORT).show()
        }

        // 2️⃣ Handle Google sign-in result
        if (requestCode == 2002) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result
                val email = account.email ?: account.account?.name

                if (email == null) {
                    Toast.makeText(this, "Email is null", Toast.LENGTH_SHORT).show()
                    return
                }

                val photoUrl = account.photoUrl
                val firebaseUser = FirebaseAuth.getInstance().currentUser

                // ✅ Add to AccountManager
                val signedInAccount = SignedInAccount(
                    email = email,
                    photoUrl = photoUrl,
                    firebaseUser = firebaseUser!!,
                    googleAccount = account
                )
                if (AccountManager.signedInAccounts.none { it.email == email }) {
                    AccountManager.signedInAccounts.add(signedInAccount)
                }
                AccountManager.currentAccount = signedInAccount

                saveAccountsToPrefs()

                // Get token in background
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val accessToken = GoogleAuthUtil.getToken(
                            this@ProfileActivity,
                            email,
                            "oauth2:${GmailScopes.GMAIL_READONLY}"
                        )

                        Log.d("AccessToken", accessToken)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Token fetched successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            updateProfileUI(email, photoUrl)
                        }

                    } catch (e: Exception) {
                        Log.e("TokenError", "Failed to get token", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Failed to get token",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else {
                Log.e("TAG", "New account sign-in failed", task.exception)
            }
        }
    }


    private fun loadAccountsFromPrefs() {
        val prefs = getSharedPreferences("accounts", MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = prefs.getString("accounts_list", null) ?: return
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String?>>>() {}.type
        val list: List<Map<String, String?>> = gson.fromJson(json, type)

        AccountManager.signedInAccounts.clear()
        list.forEach {
            AccountManager.signedInAccounts.add(
                SignedInAccount(
                    email = it["email"] ?: "",
                    photoUrl = it["photoUrl"]?.let { url -> Uri.parse(url) },
                    firebaseUser = FirebaseAuth.getInstance().currentUser ?: return@forEach,
                    googleAccount = GoogleSignIn.getLastSignedInAccount(this)
                        ?: return@forEach
                )
            )
        }
    }


    private fun updateUIWithAccount(account: SignedInAccount) {
        // Example: update profile image, email, inbox, etc.
        Glide.with(this).load(account.photoUrl).into(findViewById(R.id.profileImageView))
        findViewById<TextView>(R.id.emailTextView).text = account.email

        // You may also want to refresh fragments or reload data:
        // e.g., refreshInboxFor(account)
    }

    private fun showAccountSwitcherPopup(anchorView: View) {
        val popupView = layoutInflater.inflate(R.layout.account_list_popup, null)
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

        val accountListContainer = popupView.findViewById<LinearLayout>(R.id.accountListContainer)
        accountListContainer.removeAllViews()

        for (account in AccountManager.signedInAccounts) {
            val itemView = layoutInflater.inflate(R.layout.account_item, null)

            val imageView = itemView.findViewById<ImageView>(R.id.accountImage)
            if (account.photoUrl != null) {
                Glide.with(this).load(account.photoUrl).into(imageView)
            } else {
                imageView.setImageResource(R.drawable.icon_google) // fallback icon
            }

            itemView.setOnClickListener {
                AccountManager.currentAccount = account
                popupWindow.dismiss()
                updateUIWithAccount(account) // You'll define this
            }

            accountListContainer.addView(itemView)
        }

        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(getDrawable(android.R.drawable.dialog_holo_light_frame))
        popupWindow.isOutsideTouchable = true
        popupWindow.showAsDropDown(anchorView)
    }

    private fun showAccountPopup(anchorView: View) {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.account_list_popup, null)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        val accountListContainer = popupView.findViewById<LinearLayout>(R.id.accountListContainer)

        // Add each account to the popup
        for (account in AccountManager.signedInAccounts) {
            val itemView = inflater.inflate(R.layout.account_item, accountListContainer, false)

            val imageView = itemView.findViewById<ImageView>(R.id.accountImage)
            Glide.with(this)
                .load(account.photoUrl)
                .circleCrop()
                .into(imageView)

            // Handle switching
            itemView.setOnClickListener {
                AccountManager.currentAccount = account
                updateProfileUI(account.email, account.photoUrl)
                popupWindow.dismiss()
                // Refresh other fragments or UI if needed
            }

            accountListContainer.addView(itemView)
        }

        // Show the popup
        popupWindow.elevation = 10f
        popupWindow.showAsDropDown(anchorView)
    }



    private fun updateProfileUI(email: String?, photoUrl: Uri?) {
        val emailTextView = findViewById<TextView>(R.id.emailTextView)
        val profileImageView = findViewById<ImageView>(R.id.profileImageView)

        emailTextView.text = email ?: "No Email"

        photoUrl?.let {
            Glide.with(this)
                .load(it)
                .circleCrop()
                .into(profileImageView)
        } ?: run {
            profileImageView.setImageResource(R.drawable.icon_google) // fallback
        }

    }



    private fun logout() {
        auth.signOut()
        val intent = Intent(this, google_sign_in::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}