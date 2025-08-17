package com.example.timelymail.gmail_services

data class GmailMessage(
    val subject: String,
    val sender: String,
    val snippet: String,
    val date: String // New field
)