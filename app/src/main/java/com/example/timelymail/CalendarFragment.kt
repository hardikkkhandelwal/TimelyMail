package com.example.timelymail

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var addEventButton: Button
    private lateinit var eventCard: LinearLayout
    private lateinit var eventDate: TextView
    private lateinit var eventListContainer: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private var selectedDate: Calendar? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        // Bind views
        calendarView = view.findViewById(R.id.calendarView)
        addEventButton = view.findViewById(R.id.addEventButton)
        eventCard = view.findViewById(R.id.eventCard)
        eventDate = view.findViewById(R.id.eventDate)
        eventListContainer = view.findViewById(R.id.eventListContainer)

        eventCard.visibility = View.VISIBLE


        // Default: today
        selectedDate = Calendar.getInstance()
        loadEventsForDate(selectedDate!!.timeInMillis)

        // Listen for clicks on days
        calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                selectedDate = eventDay.calendar
                loadEventsForDate(selectedDate!!.timeInMillis)
            }
        })


        // Add Event button
        addEventButton.setOnClickListener {
            if (selectedDate != null) {
                showAddEventDialog()
            } else {
                Toast.makeText(requireContext(), "Please select a date first", Toast.LENGTH_SHORT).show()
            }
        }

        // Highlight all saved events
        highlightEventDates()

        return view
    }

    private fun showAddEventDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Add Event")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val titleInput = EditText(requireContext()).apply { hint = "Event Title" }
        val descInput = EditText(requireContext()).apply { hint = "Event Description" }

        layout.addView(titleInput)
        layout.addView(descInput)
        builder.setView(layout)

        builder.setPositiveButton("Save") { dialog, _ ->
            val title = titleInput.text.toString().trim()
            val desc = descInput.text.toString().trim()

            if (title.isNotEmpty()) {
                saveEventToFirestore(title, desc)
            } else {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun saveEventToFirestore(title: String, description: String) {
        val currentAccount = AccountManager.currentAccount ?: run {
            Toast.makeText(requireContext(), "No account selected", Toast.LENGTH_SHORT).show()
            return
        }

        val firebaseUser = currentAccount.firebaseUser
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "No Firebase user found", Toast.LENGTH_SHORT).show()
            return
        }

        val dateMillis = selectedDate?.timeInMillis ?: 0L

        val newEvent = CalendarEvent(
            id = "",
            userId = firebaseUser.uid,
            title = title,
            description = description,
            dateMillis = dateMillis
        )

        val docRef = db.collection("events").document()
        newEvent.id = docRef.id

        docRef.set(newEvent)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Event added", Toast.LENGTH_SHORT).show()
                loadEventsForDate(dateMillis)
                highlightEventDates()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error adding event", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadEventsForDate(dateMillis: Long) {
        val eventsRef = db.collection("events")

        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        eventsRef
            .whereGreaterThanOrEqualTo("dateMillis", startOfDay)
            .whereLessThanOrEqualTo("dateMillis", endOfDay)
            .get()
            .addOnSuccessListener { documents ->
                eventListContainer.removeAllViews()

                val events = documents.toObjects(CalendarEvent::class.java)

                if (events.isNotEmpty()) {
                    eventCard.visibility = View.VISIBLE
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    eventDate.text = "📅 ${sdf.format(Date(dateMillis))}"

                    for (event in events) {
                        val eventLayout = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, 8, 0, 8)
                        }

                        val eventText = TextView(requireContext()).apply {
                            text = "📌 ${event.title}\n📝 ${event.description}"
                            textSize = 16f
                            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val deleteButton = Button(requireContext()).apply {
                            text = "Delete"
                            setOnClickListener {
                                showDeleteConfirmation(event)
                            }
                        }

                        eventLayout.addView(eventText)
                        eventLayout.addView(deleteButton)
                        eventListContainer.addView(eventLayout)
                    }
                } else {
                    eventCard.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                eventListContainer.removeAllViews()
                eventCard.visibility = View.GONE
            }
    }


    private fun showDeleteConfirmation(event: CalendarEvent) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete \"${event.title}\"?")
            .setPositiveButton("Yes") { dialog, _ ->
                deleteEvent(event)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }


    private fun deleteEvent(event: CalendarEvent) {
        // Cancel alarm first
        cancelEventAlarm(event.dateMillis)

        // Delete from Firestore
        db.collection("events").document(event.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                selectedDate?.let { loadEventsForDate(it.timeInMillis) }
                highlightEventDates()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete event", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cancelEventAlarm(eventTimeInMillis: Long) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), EventAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            eventTimeInMillis.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }







    private fun highlightEventDates() {
        val currentAccount = AccountManager.currentAccount ?: return

        db.collection("events")
            .whereEqualTo("userId", currentAccount.firebaseUser!!.uid)
            .get()
            .addOnSuccessListener { documents ->
                val eventsToHighlight = mutableListOf<EventDay>()

                for (doc in documents) {
                    val event = doc.toObject(CalendarEvent::class.java)
                    val calendar = Calendar.getInstance().apply { timeInMillis = event.dateMillis }
                    eventsToHighlight.add(EventDay(calendar, R.drawable.calendar_event_circle))
                }

                calendarView.setEvents(emptyList()) // clear old highlights
                calendarView.setEvents(eventsToHighlight)
            }
    }

    fun refreshForNewUser() {
        if (selectedDate != null) {
            loadEventsForDate(selectedDate!!.timeInMillis)
            highlightEventDates()
        }
    }

    private fun scheduleEventAlarm(eventTimeInMillis: Long, eventTitle: String) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), EventAlarmReceiver::class.java).apply {
            putExtra("eventTitle", eventTitle)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            eventTimeInMillis.toInt(), // unique requestCode per event
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule the alarm
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            eventTimeInMillis,
            pendingIntent
        )
    }

}
