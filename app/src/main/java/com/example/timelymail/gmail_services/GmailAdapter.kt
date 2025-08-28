package com.example.timelymail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.example.timelymail.gmail_services.GmailMessage

class GmailAdapter(private var emailList: List<GmailMessage>) :
    RecyclerView.Adapter<GmailAdapter.EmailViewHolder>(), Filterable {

    private var filteredList: List<GmailMessage> = emailList

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
        val email = filteredList[position]
        holder.senderText.text = email.sender
        holder.subjectText.text = email.subject
        holder.snippetText.text = email.snippet
        holder.avatarText.text = email.sender.firstOrNull()?.uppercase()?.toString() ?: "?"
        holder.dateText.text = email.date
    }

    override fun getItemCount(): Int = filteredList.size

    // ✅ Implement Filterable so .filter.filter(query) works
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase()?.trim() ?: ""

                val results = if (query.isEmpty()) {
                    emailList
                } else {
                    emailList.filter {
                        it.sender.contains(query, ignoreCase = true) ||
                                it.subject.contains(query, ignoreCase = true) ||
                                it.snippet.contains(query, ignoreCase = true)
                    }
                }

                val filterResults = FilterResults()
                filterResults.values = results
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<GmailMessage> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}
