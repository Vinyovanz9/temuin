package com.temuin.temuin.data.model

data class CountryCode(
    val name: String,
    val code: String,
    val prefix: String
) {
    companion object {
        val INDONESIA = CountryCode("Indonesia", "ID", "+62")
        val MALAYSIA = CountryCode("Malaysia", "MY", "+60")
        val SINGAPORE = CountryCode("Singapore", "SG", "+65")
        val THAILAND = CountryCode("Thailand", "TH", "+66")
        val VIETNAM = CountryCode("Vietnam", "VN", "+84")
        val PHILIPPINES = CountryCode("Philippines", "PH", "+63")
        val JAPAN = CountryCode("Japan", "JP", "+81")
        val SK = CountryCode("South Korea", "KR", "+82")
        val CHINA = CountryCode("China", "CN", "+86")
        val INDIA = CountryCode("India", "IN", "+91")
        val AUSTRALIA = CountryCode("Australia", "AU", "+61")
        val US = CountryCode("United States", "US", "+1")
        val UK = CountryCode("United Kingdom", "GB", "+44")
        val GERMANY = CountryCode("Germany", "DE", "+49")
        val FRANCE = CountryCode("France", "FR", "+33")

        val countries = listOf(
            INDONESIA,
            MALAYSIA,
            SINGAPORE,
            THAILAND,
            VIETNAM,
            PHILIPPINES,
            JAPAN,
            SK,
            CHINA,
            INDIA,
            AUSTRALIA,
            UK,
            US,
            GERMANY,
            FRANCE
        )
    }

    // For dropdown items
    override fun toString(): String = "$name ($prefix)"
    
    // For the selected value display
    fun displayPrefix(): String = prefix
} 