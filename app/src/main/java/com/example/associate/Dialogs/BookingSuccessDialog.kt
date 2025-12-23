package com.example.associate.Dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.associate.databinding.DialogBookingSuccessBinding

class BookingSuccessDialog(
    context: Context,
    private val advisorName: String,
    private val userName: String,
    private val price: String,
    private val date: String,
    private val time: String,
    private val onGoHome: () -> Unit,
    private val onViewAppointment: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogBookingSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = DialogBookingSuccessBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        // Make background transparent for CardView radius
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.tvMessage.text = "You booked an appointment with $advisorName"
        binding.tvUserName.text = userName
        binding.tvPrice.text = price
        binding.tvDate.text = date
        binding.tvTime.text = time
        
        // Apply App Colors programmatically if needed, or rely on XML
        // Ensuring "Self UI" colors (Green/Gold/etc)
        binding.btnGoHome.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D8D7A")) // Green
        binding.btnViewAppointment.setTextColor(Color.parseColor("#3D8D7A"))
        
        // Update item backgrounds to Green
        binding.tvSubtitle.setTextColor(Color.parseColor("#3D8D7A"))
        
        // Update Grid Items background tint if not using a solid drawable color that matches
        // But the drawable 'rounded_item_bg' is hardcoded. 
        // We can update it via code or just set XML to use specific color.
        // XML is safer.
    }

    private fun setupListeners() {
        binding.btnGoHome.setOnClickListener {
            dismiss()
            onGoHome()
        }

        binding.btnViewAppointment.setOnClickListener {
            dismiss()
            onViewAppointment()
        }
        
        // Close on X if we added one (Reference had X), my XML didn't have X explicitly but back works.
        // I should add an X button if strict to design.
        // Adding Cancel/Dismiss listener for safely closing.
    }
}
