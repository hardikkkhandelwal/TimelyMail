package com.example.timelymail

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
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
    private lateinit var searchView: SearchView
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

                // 1️⃣ Get label info to fetch real unread count
                val label = gmailService.users().labels().get("me", labelId).execute()
                val unreadCount = label.messagesUnread ?: 0

                // 2️⃣ Fetch actual emails (optional: limit to 20 for UI)
                val response = gmailService.users().messages().list("me")
                    .setLabelIds(listOf(labelId))
                    .setMaxResults(20)
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

                withContext(Dispatchers.Main) {
                    emailList.clear()
                    emailList.addAll(fetchedEmails)
                    adapter.notifyDataSetChanged()

                    // ✅ Update badge with real unread count
                    updateAllBadges(gmailService)
                }

            } catch (e: Exception) {
                Log.e("InboxFragment", "Error fetching Gmail: ${e.message}", e)
            }
        }
    }


    fun filterEmails(query: String) {
        adapter.filter.filter(query)
    }

    private fun updateBadge(unreadCount: Int) {
        val badge = activity?.findViewById<TextView>(R.id.notificationBadge)
        badge?.let {
            if (unreadCount > 0) {
                it.visibility = View.VISIBLE
                it.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            } else {
                it.visibility = View.GONE
            }
        }
    }

    private fun updateAllBadges(gmailService: Gmail) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // INBOX unread count
                val inboxLabel = gmailService.users().labels().get("me", "INBOX").execute()
                val inboxCount = inboxLabel.messagesUnread ?: 0

                // ARCHIVE count
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

                // DRAFT count
                val draftCount = gmailService.users().drafts().list("me").execute().drafts?.size ?: 0

                // SENT count
                val sentCount = gmailService.users().messages().list("me")
                    .setLabelIds(listOf("SENT"))
                    .execute()
                    .resultSizeEstimate ?: 0

                withContext(Dispatchers.Main) {
                    // 🔹 Update main notification badge
                    updateBadge(inboxCount)

                    val inboxCountTextView = requireActivity().findViewById<TextView>(R.id.inboxCount)
                    inboxCountTextView.text = if (inboxCount > 200) "300+" else inboxCount.toString()

                    // ARCHIVE badge
                    val archiveBadge = activity?.findViewById<TextView>(R.id.archCount)
                    archiveBadge?.text = if (archiveCount > 999) "999+" else archiveCount.toString()

                    // DRAFT badge
                    val draftBadge = activity?.findViewById<TextView>(R.id.draftCount)
                    draftBadge?.text = if (draftCount > 999) "999+" else draftCount.toString()

                    // SENT badge
                    val sentBadge = activity?.findViewById<TextView>(R.id.sentCount)
                    sentBadge?.text = if (sentCount > 999) "999+" else sentCount.toString()
                }

            } catch (e: Exception) {
                Log.e("InboxFragment", "Error updating badges: ${e.message}", e)
            }
        }
    }


}
