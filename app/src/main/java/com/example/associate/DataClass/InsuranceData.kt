package com.example.associate.DataClass

data class InsurancePlan(
    val name: String,
    val company: String,
    val type: String, // "Health", "Life", "Term"
    val premium: Int, // Monthly
    val coverage: Int,
    val features: List<String>,
    val minAge: Int,
    val maxAge: Int
)

object InsuranceData {
    val plans = listOf(
        InsurancePlan(
            name = "Secure Health Plus",
            company = "Care Insure",
            type = "Health",
            premium = 500,
            coverage = 500000,
            features = listOf("Cashless Hospitalization", "No Claim Bonus", "Free Health Checkup"),
            minAge = 18,
            maxAge = 65
        ),
        InsurancePlan(
            name = "Family Floater Gold",
            company = "SafeLife",
            type = "Health",
            premium = 1200,
            coverage = 1000000,
            features = listOf("Cover for 4 members", "Maternity Benefit", "Ayush Treatment"),
            minAge = 18,
            maxAge = 70
        ),
        InsurancePlan(
            name = "Term Life Shield",
            company = "LifeGuard",
            type = "Term",
            premium = 800,
            coverage = 10000000,
            features = listOf("Critical Illness Rider", "Accidental Death Benefit"),
            minAge = 21,
            maxAge = 60
        ),
        InsurancePlan(
            name = "Senior Citizen Care",
            company = "Care Insure",
            type = "Health",
            premium = 1500,
            coverage = 300000,
            features = listOf("Pre-existing disease cover", "Home Care"),
            minAge = 60,
            maxAge = 80
        )
    )

    fun getPromptContext(): String {
        val sb = StringBuilder()
        sb.append("You are 'Insurance Buddy', an AI assistant for an Insurance Advisor app. ")
        sb.append("Use the following available insurance plans to answer user queries. Do not make up plans.\n\n")
        
        plans.forEach { plan ->
            sb.append("- Name: ${plan.name} by ${plan.company} (${plan.type})\n")
            sb.append("  Premium: ₹${plan.premium}/mo, Coverage: ₹${plan.coverage}\n")
            sb.append("  Features: ${plan.features.joinToString(", ")}\n")
            sb.append("  Age Criteria: ${plan.minAge}-${plan.maxAge} years\n\n")
        }
        
        sb.append("Rules:\n")
        sb.append("1. If user asks for comparison, compare features and premiums in a structured way.\n")
        sb.append("2. If user uploads a document (policy), summarize it or compare it with our plans.\n")
        sb.append("3. Be polite and professional. Keep answers concise.\n")
        
        return sb.toString()
    }
}
