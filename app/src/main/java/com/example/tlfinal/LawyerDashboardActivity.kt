package com.example.tlfinal

import android.app.DatePickerDialog // Keep for Add Event Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CalendarView // <<< IMPORT Standard CalendarView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper // Remove if not used elsewhere
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.databinding.LawyerDashboardBinding
import com.example.tlfinal.databinding.DialogAddEventBinding // Keep for Add Event Dialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
// Removed Material CalendarView imports
import java.text.SimpleDateFormat
import java.util.*

// ScheduleEvent Data Class (Keep as is)
data class ScheduleEvent(
    val id: String = "",
    val eventName: String = "",
    val description: String? = null,
    val date: Timestamp? = null,
    val startTime: String = "",
    val endTime: String = ""
)

// Removed: OnDateSelectedListener implementation from class declaration
class LawyerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: LawyerDashboardBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduleList = mutableListOf<ScheduleEvent>()
    private var selectedCalendar: Calendar = Calendar.getInstance() // Use Calendar for selected date

    companion object {
        private const val TAG = "LawyerDashboard"
        // Keep date/time formats
        private val DATE_FORMAT_DISPLAY = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        private val DATE_FORMAT_SHORT = SimpleDateFormat("MMM d", Locale.getDefault())
        private val TIME_FORMAT_PICKER = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupUIListeners()
        setupRecyclerView()
        setupCalendar() // Setup the standard CalendarView

        val userId = auth.currentUser?.uid
        if (userId == null) {
            handleLogout()
            return
        } else {
            loadUserDataForDrawer(userId)
        }

        // Initial load for today's schedule
        loadScheduleForDate(selectedCalendar.time) // Pass Date object
        updateSelectedDateText(selectedCalendar.time) // Pass Date object
    }

    // --- Setup Functions ---
    private fun setupUIListeners() {
        // ... (Keep Drawer, BottomNav, FAB listeners as before) ...
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navigationView: NavigationView = binding.navView
        val bottomNavigationView: BottomNavigationView = binding.bottomNavigation

        binding.imgMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        navigationView.setNavigationItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_profile -> startActivity(Intent(this, LawyerProfileActivity::class.java))
                R.id.nav_inbox -> startActivity(Intent(this, LawyerInboxActivity::class.java))
                R.id.nav_settings -> { /* TODO: Implement Settings */ }
                R.id.nav_logout -> handleLogout()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        bottomNavigationView.selectedItemId = R.id.bottom_home
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_message -> {
                    startActivity(Intent(this, LawyerInboxActivity::class.java))
                    overridePendingTransition(0,0)
                    true
                }
                R.id.bottom_home -> true
                R.id.bottom_settings -> {
                    Toast.makeText(this,"Settings Clicked", Toast.LENGTH_SHORT).show()
                    overridePendingTransition(0,0)
                    true
                }
                else -> false
            }
        }

        binding.fabAddEvent.setOnClickListener {
            showAddEventDialog(selectedCalendar.time) // Pass the current Date
        }
    }

    private fun setupRecyclerView() {
        // ... (Keep RecyclerView setup as before) ...
        scheduleAdapter = ScheduleAdapter(scheduleList) { event ->
            showEventDetailsDialog(event)
        }
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecyclerView.adapter = scheduleAdapter
    }

    private fun setupCalendar() {
        // Set listener for the standard CalendarView
        binding.calendarView.setOnDateChangeListener { view, year, month, dayOfMonth ->
            selectedCalendar.set(year, month, dayOfMonth) // Update the Calendar object
            val selectedDateInMillis = selectedCalendar.timeInMillis
            Log.d(TAG, "Date selected: $year-${month + 1}-$dayOfMonth (Millis: $selectedDateInMillis)")

            // Update UI and load data for the selected date
            updateSelectedDateText(selectedCalendar.time)
            loadScheduleForDate(selectedCalendar.time)
        }
        // Optional: Set today's date explicitly if needed (though it's the default)
        // binding.calendarView.date = System.currentTimeMillis()
    }

    // --- Date/Time Handling ---
    private fun updateSelectedDateText(date: Date) {
        binding.textSelectedDate.text = DATE_FORMAT_DISPLAY.format(date)
    }

    // Modified to accept Date
    private fun loadScheduleForDate(date: Date) {
        val userId = auth.currentUser?.uid ?: return

        binding.textNoSchedule.visibility = View.GONE
        binding.scheduleRecyclerView.visibility = View.INVISIBLE

        // Calculate start and end Timestamps for the selected day using the passed Date
        val startOfDay = Calendar.getInstance().apply {
            time = date // Set to selected date
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        val startTimestamp = Timestamp(startOfDay.time)
        val endTimestamp = Timestamp(endOfDay.time)

        Log.d(TAG, "Fetching schedule for date: $date, Start: $startTimestamp, End: $endTimestamp")

        firestore.collection("lawyers").document(userId)
            .collection("schedule")
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThanOrEqualTo("date", endTimestamp)
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                scheduleList.clear()
                if (querySnapshot.isEmpty) {
                    Log.d(TAG, "No schedule found for this date.")
                    binding.textNoSchedule.visibility = View.VISIBLE
                    binding.scheduleRecyclerView.visibility = View.GONE
                } else {
                    Log.d(TAG, "Found ${querySnapshot.size()} schedule events.")
                    querySnapshot.documents.forEach { document ->
                        try {
                            val event = document.toObject(ScheduleEvent::class.java)?.copy(id = document.id)
                            event?.let { scheduleList.add(it) }
                        } catch (e: Exception) { Log.e(TAG, "Error mapping schedule doc ${document.id}", e)}
                    }
                    scheduleList.sortBy { parseTime(it.startTime) }
                    binding.textNoSchedule.visibility = View.GONE
                    binding.scheduleRecyclerView.visibility = View.VISIBLE
                }
                scheduleAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching schedule", e)
                Toast.makeText(this, "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.textNoSchedule.text = "Error loading schedule."
                binding.textNoSchedule.visibility = View.VISIBLE
                binding.scheduleRecyclerView.visibility = View.GONE
            }
    }

    private fun parseTime(timeString: String): Date? {
        return try { TIME_FORMAT_PICKER.parse(timeString) } catch (e: Exception) { null }
    }

    // --- Add/Edit/Delete Event Dialogs ---
    // Modified to accept Date
    private fun showAddEventDialog(forDate: Date) {
        val dialogBinding = DialogAddEventBinding.inflate(LayoutInflater.from(this))

        // Keep the selected date (passed as 'forDate')
        val selectedDateCalendar = Calendar.getInstance().apply { time = forDate }
        dialogBinding.tvSelectedDate.text = DATE_FORMAT_DISPLAY.format(selectedDateCalendar.time)

        val startTimeCalendar = Calendar.getInstance()
        val endTimeCalendar = Calendar.getInstance()

        dialogBinding.tvStartTime.setOnClickListener {
            showTimePicker(startTimeCalendar) { calendar ->
                dialogBinding.tvStartTime.text = TIME_FORMAT_PICKER.format(calendar.time)
                endTimeCalendar.time = calendar.time
                endTimeCalendar.add(Calendar.HOUR_OF_DAY, 1)
                dialogBinding.tvEndTime.text = TIME_FORMAT_PICKER.format(endTimeCalendar.time)
            }
        }
        dialogBinding.tvEndTime.setOnClickListener {
            showTimePicker(endTimeCalendar) { calendar ->
                dialogBinding.tvEndTime.text = TIME_FORMAT_PICKER.format(calendar.time)
            }
        }

        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.btnCreateEvent.setOnClickListener {
            val eventName = dialogBinding.etEventName.text.toString().trim()
            val description = dialogBinding.etEventDescription.text.toString().trim()
            val startTime = dialogBinding.tvStartTime.text.toString()
            val endTime = dialogBinding.tvEndTime.text.toString()

            if (eventName.isBlank() || startTime == "Select Time" || endTime == "Select Time") {
                Toast.makeText(this, "Please fill event name, start time, and end time.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Combine selected date (from forDate) with start time for the timestamp
            val eventStartDateTime = Calendar.getInstance().apply {
                time = forDate // Start with the selected date passed to the dialog
                try {
                    val parsedStartTime = TIME_FORMAT_PICKER.parse(startTime)
                    if (parsedStartTime != null) {
                        val startCal = Calendar.getInstance().apply { time = parsedStartTime }
                        set(Calendar.HOUR_OF_DAY, startCal.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, startCal.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }
                } catch (e: Exception) { Log.e(TAG, "Error parsing start time: $startTime", e)}
            }

            saveEventToFirestore(
                eventName = eventName,
                description = description.takeIf { it.isNotBlank() },
                date = Timestamp(eventStartDateTime.time), // Use combined date/time
                startTime = startTime,
                endTime = endTime
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTimePicker(calendar: Calendar, onTimeSet: (Calendar) -> Unit) {
        // ... (Keep existing showTimePicker logic) ...
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            onTimeSet(calendar)
        }
        TimePickerDialog(this, timeSetListener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun saveEventToFirestore(eventName: String, description: String?, date: Timestamp, startTime: String, endTime: String) {
        // ... (Keep existing saveEventToFirestore logic, ensure it uses selectedCalendar.time for refresh) ...
        val userId = auth.currentUser?.uid ?: return
        val newEvent = ScheduleEvent(eventName = eventName, description = description, date = date, startTime = startTime, endTime = endTime)

        firestore.collection("lawyers").document(userId)
            .collection("schedule")
            .add(newEvent)
            .addOnSuccessListener {
                Log.d(TAG, "Event added successfully with ID: ${it.id}")
                Toast.makeText(this, "Schedule added", Toast.LENGTH_SHORT).show()
                loadScheduleForDate(selectedCalendar.time) // Refresh using the currently selected date
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding event", e)
                Toast.makeText(this, "Error adding schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEventDetailsDialog(event: ScheduleEvent) {
        // ... (Keep existing showEventDetailsDialog logic) ...
        val details = """
             Event: ${event.eventName}
             Date: ${event.date?.toDate()?.let { DATE_FORMAT_SHORT.format(it) } ?: "N/A"}
             Time: ${event.startTime} - ${event.endTime}
             Notes: ${event.description ?: "None"}
         """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Schedule Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNegativeButton("Delete") { _, _ -> promptDeleteEvent(event) }
            .show()
    }

    private fun promptDeleteEvent(event: ScheduleEvent) {
        // ... (Keep existing promptDeleteEvent logic) ...
        AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.eventName}'?")
            .setPositiveButton("Delete") { _, _ -> deleteEventFromFirestore(event) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEventFromFirestore(event: ScheduleEvent) {
        // ... (Keep existing deleteEventFromFirestore logic, ensure it uses selectedCalendar.time for refresh) ...
        val userId = auth.currentUser?.uid ?: return
        if (event.id.isBlank()) return

        firestore.collection("lawyers").document(userId)
            .collection("schedule").document(event.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Event deleted successfully: ${event.id}")
                Toast.makeText(this, "Schedule deleted", Toast.LENGTH_SHORT).show()
                loadScheduleForDate(selectedCalendar.time) // Refresh
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting event: ${event.id}", e)
                Toast.makeText(this, "Error deleting schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Other Functions (Keep loadUserDataForDrawer, handleLogout, ScheduleAdapter) ---
    private fun loadUserDataForDrawer(userId: String) {
        // ...(Keep existing logic)...
        firestore.collection("lawyers").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val middleName = document.getString("middleName")
                    val fullName = "$firstName ${middleName?.firstOrNull()?.let { "$it." } ?: ""} $lastName".trim()

                    val headerView = binding.navView.getHeaderView(0)
                    val userNameTextView: TextView? = headerView.findViewById(R.id.user_name_header)
                    userNameTextView?.text = fullName ?: "Lawyer"
                } else { Log.w(TAG, "User document not found for drawer header.") }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error loading user data for drawer", e) }
    }

    private fun handleLogout() {
        // ...(Keep existing logic)...
        auth.signOut()
        Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // --- Schedule Adapter (Keep as is) ---
    class ScheduleAdapter(
        private val scheduleList: List<ScheduleEvent>,
        private val onItemClick: (ScheduleEvent) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val eventName: TextView = view.findViewById(R.id.tvEventName)
            val eventTime: TextView = view.findViewById(R.id.tvEventTime)
            val eventDescription: TextView = view.findViewById(R.id.tvEventDescription)

            init {
                itemView.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onItemClick(scheduleList[adapterPosition])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_schedule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = scheduleList[position]
            holder.eventName.text = event.eventName
            holder.eventTime.text = "${event.startTime} - ${event.endTime}"
            if (!event.description.isNullOrBlank()) {
                holder.eventDescription.text = event.description
                holder.eventDescription.visibility = View.VISIBLE
            } else {
                holder.eventDescription.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = scheduleList.size
    }

}