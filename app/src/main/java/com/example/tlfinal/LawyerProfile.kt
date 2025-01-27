package com.example.tlfinal

data class LawyerProfile(
    var fullName: String? = null,
    var contactNo: String? = null,
    var email: String? = null,
    var username: String? = null,
    var address: String? = null,
    var gender: String? = null,
    var age: Int? = null,
    var availability: String? = null,
    var legalservices: String? = null,
    var prelawdegree: String? = null,
    var lawschool: String? = null,
    var yearsofexp: Int? = null,
    var consultationfee: Int? = null,
    var hours: MutableList<Map<String, String>>? = null
)