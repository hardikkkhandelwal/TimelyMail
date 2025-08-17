package com.example.timelymail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.timelymail.gmail_services.GmailMessage

class GmailAdapter(private val emailList: List<GmailMessage>) :
    RecyclerView.Adapter<GmailAdapter.EmailViewHolder>() {

    inner class EmailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarText: TextView = itemView.findViewById(R.id.avatarText)
        val senderText: TextView = itemView.findViewById(R.id.senderText)
        val subjectText: TextView = itemView.findViewById(R.id.subjectText)
        val snippetText: TextView = itemView.findViewById(R.id.snippetText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_email, parent, false)
        return EmailViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmailViewHolder, position: Int) {
        val email = emailList[position]
        holder.senderText.text = email.sender
        holder.subjectText.text = email.subject
        holder.snippetText.text = email.snippet
        holder.avatarText.text = email.sender.firstOrNull()?.uppercase()?.toString() ?: "?"

        // Format date nicely if you store it in ISO format
        holder.dateText.text = email.date
    }
    override fun getItemCount(): Int = emailList.size
}
