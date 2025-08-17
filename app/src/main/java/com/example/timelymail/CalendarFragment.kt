package com.example.timelymail

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
    private var selectedDate: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        addEventButton = view.findViewById(R.id.addEventButton)
        eventCard = view.findViewById(R.id.eventCard)
        eventDate = view.findViewById(R.id.eventDate)
        eventListContainer = view.findViewById(R.id.eventListContainer)

        eventCard.visibility = View.GONE

        // Initialize selected date as today
        val today = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = sdf.format(today.time)

        // Load events for the selected date
        loadEventsForDate(selectedDate)

        // Date change listener
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            selectedDate = sdf.format(calendar.time)
            loadEventsForDate(selectedDate)
        }

        addEventButton.setOnClickListener {
            if (selectedDate.isNotEmpty()) {
                showAddEventDialog()
            } else {
                Toast.makeText(requireContext(), "Please select a date first", Toast.LENGTH_SHORT).show()
            }
        }

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

            if (title.isNotEmpty()) saveEventToFirestore(title, desc)
            else Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
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

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateMillis = sdf.parse(selectedDate)?.time ?: 0L

        val firebaseUser = currentAccount.firebaseUser
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "No Firebase user found", Toast.LENGTH_SHORT).show()
            return
        }

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
                loadEventsForDate(selectedDate)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error adding event", Toast.LENGTH_SHORT).show()
            }
    }

    fun loadEventsForDate(date: String) {
        val currentAccount = AccountManager.currentAccount ?: return

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateMillis = sdf.parse(date)?.time ?: 0L

        db.collection("events")
            .whereEqualTo("userId", currentAccount.firebaseUser!!.uid)
            .whereEqualTo("dateMillis", dateMillis)
            .get()
            .addOnSuccessListener { documents ->
                eventListContainer.removeAllViews()
                eventCard.visibility = View.VISIBLE
                eventDate.text = "📅 $date"

                if (!documents.isEmpty) {
                    val events = documents.toObjects(CalendarEvent::class.java)
                    for (event in events) {
                        val eventText = TextView(requireContext()).apply {
                            text = "📌 Your Event: ${event.title}\n📌 Your Description: ${event.description}"
                            textSize = 16f
                            setPadding(0, 8, 0, 8)
                            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                        }
                        eventListContainer.addView(eventText)
                    }
                } else {
                    val noEventText = TextView(requireContext()).apply {
                        text = "No events for this date."
                        setPadding(0, 8, 0, 8)
                    }
                    eventListContainer.addView(noEventText)
                }
            }
            .addOnFailureListener {
                eventCard.visibility = View.VISIBLE
                eventDate.text = "📅 $date"
                eventListContainer.removeAllViews()
                val errorText = TextView(requireContext()).apply {
                    text = "Failed to load events."
                }
                eventListContainer.addView(errorText)
            }
    }

    /** Call this method when user switches account */
    fun refreshForNewUser() {
        if (selectedDate.isNotEmpty()) {
            loadEventsForDate(selectedDate)
        }
    }
}
