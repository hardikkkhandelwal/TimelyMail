package com.example.timelymail

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseUser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SignedInAccount(
    val email: String,
    val photoUrl: Uri?,
    val firebaseUser: FirebaseUser?,
    val googleAccount: GoogleSignInAccount?
)

object AccountManager {
    val signedInAccounts = mutableListOf<SignedInAccount>()
    var currentAccount: SignedInAccount? = null
}

