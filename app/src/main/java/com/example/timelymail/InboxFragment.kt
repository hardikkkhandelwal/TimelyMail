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

    private var labelId: String = "INBOX" // default



    companion object {
        fun newInstance(labelId: String): InboxFragment {
            val fragment = InboxFragment()
            val args = Bundle()
            args.putString("labelId", labelId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        labelId = arguments?.getString("labelId") ?: "INBOX"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_email_list, container, false)
        recyclerView = view.findViewById(R.id.inboxRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = GmailAdapter(emailList)
        recyclerView.adapter = adapter

        fetchEmails()
        return view
    }

    override fun onResume() {
        super.onResume()
        fetchEmails()
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

                // 🔑 Fetch emails for this label (Inbox or any)
                val response = gmailService.users().messages().list("me")
                    .setLabelIds(listOf(labelId))
                    .setMaxResults(10)
                    .execute()

                val messages = response.messages ?: emptyList()
                val fetchedEmails = mutableListOf<GmailMessage>()

                for (msg in messages) {
                    val fullMessage = gmailService.users().messages().get("me", msg.id).execute()
                    val headers = fullMessage.payload?.headers ?: emptyList()

                    val subject = headers.firstOrNull { it.name.equals("Subject", true) }?.value ?: "(No Subject)"
                    val from = headers.firstOrNull { it.name.equals("From", true) }?.value ?: "(No Sender)"
                    val snippet = fullMessage.snippet ?: "(No Snippet)"
                    val internalDateMillis = fullMessage.internalDate ?: 0
                    val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                        .format(java.util.Date(internalDateMillis))

                    fetchedEmails.add(GmailMessage(subject, from, snippet, date))
                }

                // 📦 Fetch badge counts
                val inboxCount = gmailService.users().messages().list("me")
                    .setLabelIds(listOf("INBOX"))
                    .setMaxResults(1)
                    .execute()
                    .resultSizeEstimate ?: 0

                var archiveCount = 0
                var pageToken: String? = null
                do {
                    val archResponse = gmailService.users().messages().list("me")
                        .setQ("-in:inbox -in:spam -in:trash")
                        .setMaxResults(100)
                        .setPageToken(pageToken)
                        .execute()
                    archiveCount += archResponse.messages?.size ?: 0
                    pageToken = archResponse.nextPageToken
                } while (pageToken != null)

                val draftCount = gmailService.users().drafts().list("me").execute().drafts?.size ?: 0

                val sentCount = gmailService.users().messages().list("me")
                    .setLabelIds(listOf("SENT"))
                    .execute()
                    .resultSizeEstimate ?: 0

                // ⬇ Update UI on main thread
                withContext(Dispatchers.Main) {
                    // RecyclerView update
                    emailList.clear()
                    emailList.addAll(fetchedEmails)
                    adapter.notifyDataSetChanged()

                    // Update badge TextViews
                    val inboxCountTextView = requireActivity().findViewById<TextView>(R.id.inboxCount)
                    inboxCountTextView.text = if (inboxCount > 200) "300+" else inboxCount.toString()

                    val archiveCountTextView = requireActivity().findViewById<TextView>(R.id.archCount)
                    archiveCountTextView.text = if (archiveCount > 999) "999+" else archiveCount.toString()

                    val draftCountTextView = requireActivity().findViewById<TextView>(R.id.draftCount)
                    draftCountTextView.text = if (draftCount > 999) "999+" else draftCount.toString()

                    val sentCountTextView = requireActivity().findViewById<TextView>(R.id.sentCount)
                    sentCountTextView.text = if (sentCount > 999) "999+" else sentCount.toString()
                }

            } catch (e: Exception) {
                Log.e("InboxFragment", "Error fetching Gmail: ${e.message}", e)
            }
        }
    }

}

