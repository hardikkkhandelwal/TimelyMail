package com.example.timelymail
// CalendarEvent.kt
data class CalendarEvent(
    var id: String = "",               // Firestore document ID
    var userId: String = "",           // Firebase UID or email
    var title: String = "",
    var description: String = "",
    var dateMillis: Long = 0L          // store date as milliseconds
)
