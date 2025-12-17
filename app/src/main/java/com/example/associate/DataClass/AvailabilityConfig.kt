package com.example.associate.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.firebase.firestore.PropertyName

@Parcelize
data class InstantAvailabilityConfig(
    @get:PropertyName("chatEnabled") @set:PropertyName("chatEnabled") var isChatEnabled: Boolean = false,
    @get:PropertyName("audioCallEnabled") @set:PropertyName("audioCallEnabled") var isAudioCallEnabled: Boolean = false,
    @get:PropertyName("videoCallEnabled") @set:PropertyName("videoCallEnabled") var isVideoCallEnabled: Boolean = false
) : Parcelable

@Parcelize
data class ScheduledAvailabilityConfig(
    @get:PropertyName("chatEnabled") @set:PropertyName("chatEnabled") var isChatEnabled: Boolean = false,
    @get:PropertyName("audioCallEnabled") @set:PropertyName("audioCallEnabled") var isAudioCallEnabled: Boolean = false,
    @get:PropertyName("videoCallEnabled") @set:PropertyName("videoCallEnabled") var isVideoCallEnabled: Boolean = false,
    @get:PropertyName("inPersonEnabled") @set:PropertyName("inPersonEnabled") var isInPersonEnabled: Boolean = false,
    @get:PropertyName("officeVisitEnabled") @set:PropertyName("officeVisitEnabled") var isOfficeVisitEnabled: Boolean = false
) : Parcelable
