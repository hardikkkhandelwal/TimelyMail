package com.example.timelymail

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Message.RecipientType
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import android.util.Base64

class ComposeEmailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_email)

        val fromField = findViewById<EditText>(R.id.editFrom)
        val toField = findViewById<EditText>(R.id.editTo)
        val subjectField = findViewById<EditText>(R.id.editSubject)
        val messageField = findViewById<EditText>(R.id.editMessage)
        val sendButton = findViewById<Button>(R.id.btnSend)

        // ✅ Get the currently signed-in account
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            fromField.setText(account.email) // Show logged-in email in "From"
        } else {
            Toast.makeText(this, "No signed-in account found!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sendButton.setOnClickListener {
            val to = toField.text.toString()
            val subject = subjectField.text.toString()
            val messageText = messageField.text.toString()

            if (to.isNotEmpty() && subject.isNotEmpty() && messageText.isNotEmpty()) {
                sendEmail(account.email ?: "", to, subject, messageText, account.email ?: "")
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendEmail(from: String, to: String, subject: String, bodyText: String, accountEmail: String) {
        Thread {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    this,
                    listOf(GmailScopes.GMAIL_SEND)
                )
                credential.selectedAccountName = accountEmail

                val account = GoogleSignIn.getLastSignedInAccount(this)
                if (account != null) {
                    credential.selectedAccount = account.account
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "No signed-in account available!", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val gmailService = Gmail.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("TimelyMail").build()

                val props = Properties()
                val session = Session.getDefaultInstance(props, null)
                val email = MimeMessage(session)
                email.setFrom(InternetAddress(from))
                email.addRecipient(RecipientType.TO, InternetAddress(to))
                email.subject = subject
                email.setText(bodyText)

                val buffer = ByteArrayOutputStream()
                email.writeTo(buffer)

                // ✅ Correct Base64 encoding: URL_SAFE + NO_WRAP
                val encodedEmail = Base64.encodeToString(buffer.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
                val message = Message().setRaw(encodedEmail)

                gmailService.users().messages().send("me", message).execute()

                runOnUiThread {
                    Toast.makeText(this, "Email sent!", Toast.LENGTH_SHORT).show()
                    finish()
                }

            } catch (e: GoogleAuthIOException) {
                runOnUiThread {
                    Toast.makeText(this, "Auth error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

}
