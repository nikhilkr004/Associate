package com.example.associate.DataClass

data class FinancialProfile (
    val goals: Map<String, String>, // e.g., "short_term" to "Buy a house"
    val incomeRange: String, // e.g., "50,000-100,000 USD"
    val employmentStatus: String, // e.g., "Salaried", "Self-Employed"
    val existingInvestments: List<String>, // e.g., ["Stocks", "Mutual Funds"]
    val riskAppetite: String // e.g., "Low", "Medium", "High"
)