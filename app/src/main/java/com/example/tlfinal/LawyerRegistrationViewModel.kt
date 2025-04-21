package com.example.tlfinal

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LawyerRegistrationViewModel : ViewModel() {

    val lawyerData = MutableLiveData<LawyerData>()
    data class LawyerData(
        val firstName: String? = null,
        val middleName: String? = null,
        val lastName: String? = null,
        val age: Int? = null,
        val gender: String? = null,
        val contactNumber: String? = null,
        val emailAddress: String? = null,
        val officeAddress: String? = null,
        val rollNumber: String? = null,
        val legalSpecializations: List<Pair<String, List<String>>> = listOf(),
        val consultationFee: Double? = null,
        val availabilityOption: AvailabilityOption? = null,
        val daysAndHours: List<DayHours> = listOf(),
        val affiliation: String? = null,
        val lawFirmName: String? = null,
        val lawFirmAddress: String? = null,
        val username: String? = null,
        val password: String? = null,
        val confirmPassword: String? = null, // Add confirm password field
        val termsAccepted: Boolean = false,
        val yearsOfExperience: Int? = null
    ) {

    }

    enum class AvailabilityOption {
        NO_HOURS, ALWAYS_OPEN, PERMANENTLY_CLOSED, TEMPORARILY_CLOSED, OPEN_SELECTED_HOURS
    }

    data class DayHours(val day: String, val startTime: String, val endTime: String)
}