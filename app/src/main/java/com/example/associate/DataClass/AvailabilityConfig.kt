package com.example.associate.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.firebase.firestore.PropertyName

@Parcelize
@com.google.firebase.firestore.IgnoreExtraProperties
data class InstantAvailabilityConfig(
    @get:PropertyName("chatEnabled") @set:PropertyName("chatEnabled") var isChatEnabled: Boolean = false,
    @get:PropertyName("audioCallEnabled") @set:PropertyName("audioCallEnabled") var isAudioCallEnabled: Boolean = false,
    @get:PropertyName("videoCallEnabled") @set:PropertyName("videoCallEnabled") var isVideoCallEnabled: Boolean = false
) : Parcelable

@Parcelize
@com.google.firebase.firestore.IgnoreExtraProperties
data class ScheduledAvailabilityConfig(
    @get:PropertyName("chatEnabled") @set:PropertyName("chatEnabled") var isChatEnabled: Boolean = false,
    @get:PropertyName("audioCallEnabled") @set:PropertyName("audioCallEnabled") var isAudioCallEnabled: Boolean = false,
    @get:PropertyName("videoCallEnabled") @set:PropertyName("videoCallEnabled") var isVideoCallEnabled: Boolean = false,
    @get:PropertyName("inPersonEnabled") @set:PropertyName("inPersonEnabled") var isInPersonEnabled: Boolean = false,
    @get:PropertyName("officeVisitEnabled") @set:PropertyName("officeVisitEnabled") var isOfficeVisitEnabled: Boolean = false
) : Parcelable

// Updated for repository activity
