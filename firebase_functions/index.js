const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

// Function 1: Send notification when user creates a booking (for Advisor)
exports.sendBookingNotification = onDocumentCreated(
  "instant_bookings/{bookingId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.log("No data associated with the event");
      return null;
    }

    const bookingData = snapshot.data();
    const bookingId = event.params.bookingId;

    console.log(`📋 New Booking Created: ${bookingId}`);

    const advisorId = bookingData.advisorId;
    const studentName = bookingData.studentName || "Student";

    if (!advisorId) {
      console.log("❌ No advisorId found in booking data");
      return null;
    }

    try {
      // Get advisor's FCM token from 'advisors' collection
      const advisorDoc = await admin
        .firestore()
        .collection("advisors")
        .doc(advisorId)
        .get();

      if (!advisorDoc.exists) {
        console.log(`❌ Advisor document not found for ID: ${advisorId}`);
        return null;
      }

      const advisorData = advisorDoc.data();
      const fcmToken = advisorData.fcmToken;

      if (!fcmToken) {
        console.log(`❌ No FCM token found for advisor: ${advisorId}`);
        return null;
      }

      console.log(`✅ Found FCM token for advisor: ${advisorId}`);

      // Construct payload for booking notification
      const payload = {
        token: fcmToken,
        notification: {
          title: "New Session Request 🎯",
          body: `${studentName} wants to connect with you`,
        },
        data: {
          type: "instant_booking",
          bookingId: bookingId,
          studentName: studentName,
          advisorId: advisorId,
          notificationType: "booking_request",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "advisor_session_alerts",
            priority: "high",
            sound: "default",
          },
        },
      };

      // Send the message
      const response = await admin.messaging().send(payload);
      console.log("🚀 Booking notification sent successfully:", response);

      return { success: true, messageId: response };
    } catch (error) {
      console.error("❌ Error sending booking notification:", error);
      return null;
    }
  }
);



// Function 2: Send notification when advisor calls user
exports.sendIncomingCallNotification = onDocumentCreated(
  "videoCalls/{callId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.log("No data associated with the event");
      return null;
    }

    const callData = snapshot.data();
    const callId = event.params.callId;
    const receiverId = callData.receiverId;
    // Support both field names from Advisor App
    const callerName = callData.advisorName || callData.callerName || "Advisor";
    const channelName = callData.channelName;
    const advisorId = callData.callerId || callData.advisorId;

    // Get call type (Default to VIDEO)
    const callType = callData.callType || "VIDEO";

    console.log(`📞 New Call Initiated: ${callId} [${callType}] to ${receiverId}`);

    if (!receiverId) {
      console.log("❌ No receiverId found in call data");
      return null;
    }

    try {
      // Get user's FCM token from 'users' collection
      const userDoc = await admin
        .firestore()
        .collection("users")
        .doc(receiverId)
        .get();

      if (!userDoc.exists) {
        console.log(`❌ User document not found for ID: ${receiverId}`);
        return null;
      }

      const userData = userDoc.data();
      const fcmToken = userData.fcmToken;

      if (!fcmToken) {
        console.log(`❌ No FCM token found for user: ${receiverId}`);
        return null;
      }

      console.log(`✅ Found FCM token for user: ${receiverId}`);

      // Fetch advisor details to get avatar and name
      let advisorAvatar = "";
      let fetchedAdvisorName = "";

      if (advisorId) {
        try {
          const advisorDoc = await admin.firestore().collection("advisors").doc(advisorId).get();
          if (advisorDoc.exists) {
            const data = advisorDoc.data();
            // Handle nested structure (AdvisorDataClass format)
            if (data.basicInfo) {
              advisorAvatar = data.basicInfo.profileImage || "";
              fetchedAdvisorName = data.basicInfo.name || "";
            } else {
              // Fallback for flat structure
              advisorAvatar = data.profileImage || data.profileimage || "";
              fetchedAdvisorName = data.name || data.advisorName || "";
            }
            console.log(`✅ Found advisor: ${fetchedAdvisorName}, Avatar: ${advisorAvatar}`);
          }
        } catch (e) {
          console.error("Error fetching advisor details:", e);
        }
      }

      // Use fetched name if available, otherwise fallback to call data
      const finalCallerName = fetchedAdvisorName || callerName;

      // Dynamic Title & Body (Sent in Data Payload for local handling)
      const isVideo = callType === "VIDEO";
      //   const notificationTitle = isVideo ? "Incoming Video Call 🎥" : "Incoming Audio Call 📞";
      //   const notificationBody = isVideo 
      //       ? `${finalCallerName} is inviting you to a video call...` 
      //       : `${finalCallerName} is inviting you to an audio call...`;

      // Construct payload for call notification (High Priority Data Message)
      // IMPORTANT: Do NOT include 'notification' key. This ensures onMessageReceived
      // triggers even in background/killed state.
      const payload = {
        token: fcmToken,
        data: {
          type: "call", // Keep consistent with app expectation
          callId: callId,
          channelName: channelName,
          title: finalCallerName, // App uses this for caller name
          advisorName: finalCallerName,
          advisorAvatar: advisorAvatar,
          advisorId: advisorId,
          callType: callType, // Pass callType to App
          // priority: "high",
          //   notificationType: isVideo ? "video_call" : "audio_call",
          click_action: "FLUTTER_NOTIFICATION_CLICK"
        },
        android: {
          priority: "high",
          ttl: 0 // Immediate delivery
        },
        apns: {
          payload: {
            aps: {
              contentAvailable: true, // For iOS background fetch
              priority: "10"
            }
          }
        }
      };

      // Send the message
      const response = await admin.messaging().send(payload);
      console.log("🚀 Call notification sent successfully:", response);

      return { success: true, messageId: response };
    } catch (error) {
      console.error("❌ Error sending call notification:", error);
      return null;
    }
  }
);
