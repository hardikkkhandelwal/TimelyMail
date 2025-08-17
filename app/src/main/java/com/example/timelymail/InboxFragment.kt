package com.example.timelymail

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.timelymail.gmail_services.GmailMessage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.gmail.Gmail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InboxFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GmailAdapter
    private var emailList: MutableList<GmailMessage> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_email_list, container, false)
        recyclerView = view.findViewById(R.id.inboxRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = GmailAdapter(emailList)
        recyclerView.adapter = adapter

        val token = arguments?.getString("token")
        fetchEmails()

        return view
    }
    override fun onResume() {
        super.onResume()
        fetchEmails() // This ensures inbox is updated every time fragment is resumed
    }


    fun fetchEmails() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                val credential = GoogleAccountCredential.usingOAuth2(
                    requireContext(), listOf("https://www.googleapis.com/auth/gmail.readonly")
                )
                credential.selectedAccount = account?.account

                val gmailService = Gmail.Builder(
                    NetHttpTransport(),
                    JacksonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("TimelyMail").build()

                val response = gmailService.users().messages().list("me")
                    .setMaxResults(10)
                    .execute()

                val messages = response.messages ?: emptyList()
                val fetchedEmails = mutableListOf<GmailMessage>()

                for (msg in messages) {
                    try {
                        val fullMessage = gmailService.users().messages().get("me", msg.id).execute()
                        val headers = fullMessage.payload?.headers ?: emptyList()

                        val subject = headers.firstOrNull { it.name.equals("Subject", true) }?.value ?: "(No Subject)"
                        val from = headers.firstOrNull { it.name.equals("From", true) }?.value ?: "(No Sender)"
                        val snippet = fullMessage.snippet ?: "(No Snippet)"
                        val internalDateMillis = fullMessage.internalDate ?: 0
                        val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                            .format(java.util.Date(internalDateMillis))

                        fetchedEmails.add(GmailMessage(subject, from, snippet, date))
                    } catch (e: Exception) {
                        Log.e("InboxFragment", "Error parsing message: ${e.message}", e)
                    }
                }

                // ⬇️ Get email count
                val inboxCount = gmailService.users().messages()
                    .list("me")
                    .setLabelIds(listOf("INBOX"))
                    .setMaxResults(1)
                    .execute()
                    .resultSizeEstimate ?: 0

                // 📦 Accurate archive count logic
                var archiveCount = 0
                var pageToken: String? = null

                do {
                    val response = gmailService.users().messages().list("me")
                        .setQ("-in:inbox -in:spam -in:trash") // Exclude inbox, spam, trash = archive
                        .setMaxResults(100)
                        .setPageToken(pageToken)
                        .execute()

                    archiveCount += response.messages?.size ?: 0
                    pageToken = response.nextPageToken
                } while (pageToken != null)

                // 📝 Get Draft Count
                val draftList = gmailService.users().drafts().list("me").execute()
                val draftCount = draftList.drafts?.size ?: 0

                // 📤 Get Sent Count
                val query = "after:2024/01/01 from:me"
                val sentList = gmailService.users().messages().list("me")
                    .setLabelIds(listOf("SENT"))
                    .setQ(query)
                    .execute()
                val sentCount = sentList.resultSizeEstimate ?: 0





                withContext(Dispatchers.Main) {
                    // Update list
                    emailList.clear()
                    emailList.addAll(fetchedEmails)
                    adapter.notifyDataSetChanged()

                    // ✅ Update badge count with cap
                    val displayCount = if (inboxCount > 200) "300+" else inboxCount.toString()
                    val inboxCountTextView = requireActivity().findViewById<TextView>(R.id.inboxCount)
                    inboxCountTextView.text = displayCount

                    val archiveDisplay = if (archiveCount > 999) "999+" else archiveCount.toString()
                    val archiveCountTextView = requireActivity().findViewById<TextView>(R.id.archCount)
                    archiveCountTextView.text = archiveDisplay

                    // 📝 Update Draft Count UI
                    val draftDisplay = if (draftCount > 999) "999+" else draftCount.toString()
                    val draftCountTextView = requireActivity().findViewById<TextView>(R.id.draftCount)
                    draftCountTextView.text = draftDisplay

                    // 📤 Update Sent Count UI
                    val sentDisplay = if (sentCount > 999) "999+" else sentCount.toString()
                    val sentCountTextView = requireActivity().findViewById<TextView>(R.id.sentCount)
                    sentCountTextView.text = sentDisplay


                }


            } catch (e: Exception) {
                Log.e("InboxFragment", "Error fetching Gmail: ${e.message}", e)
            }
        }
    }


}
