const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

// ==========================================
// 1. NOTIFICATION FUNCTIONS
// ==========================================

// Function 1: Send notification when user creates a booking (for Advisor) + Save to Activity Feed
exports.sendBookingNotification = onDocumentCreated(
  "instant_bookings/{bookingId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return null;
    const bookingData = snapshot.data();
    const bookingId = event.params.bookingId;
    const advisorId = bookingData.advisorId;
    const studentName = bookingData.studentName || "Student";

    console.log(`📋 New Booking Created: ${bookingId} for Advisor: ${advisorId}`);

    if (!advisorId) return null;

    try {
      const db = admin.firestore();
      const advisorRef = db.collection("advisors").doc(advisorId);
      const advisorDoc = await advisorRef.get();

      if (!advisorDoc.exists) {
        console.log(`❌ Advisor not found: ${advisorId}`);
        return null;
      }

      // A. Send FCM Notification
      const fcmToken = advisorDoc.data().fcmToken;
      if (fcmToken) {
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
          android: { priority: "high", notification: { channelId: "advisor_session_alerts", priority: "high", sound: "default" } },
        };
        await admin.messaging().send(payload);
        console.log("✅ FCM sent to Advisor");
      }

      // B. Save to Activity Feed (NEW)
      // Saving to 'notifications' subcollection for local feed display
      await advisorRef.collection("notifications").add({
        title: "New Session Request",
        message: `${studentName} requested a session.`,
        bookingId: bookingId,
        type: "booking_request",
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        read: false
      });
      console.log("✅ Notification saved to Activity Feed");

      return { success: true };
    } catch (error) {
      console.error("❌ Error in sendBookingNotification:", error);
      return null;
    }
  }
);

// Function 2: Send notification when advisor calls user
// Helper Function for Incoming Call/Chat Notifications
const handleIncomingCallNotification = async (event) => {
  const snapshot = event.data;
  if (!snapshot) return null;
  const callData = snapshot.data();
  const callId = event.params.callId;
  const receiverId = callData.receiverId;
  const callerName = callData.advisorName || callData.callerName || "Advisor";
  const channelName = callData.channelName;
  const advisorId = callData.callerId || callData.advisorId;
  // Default to VIDEO if missing, but check collection context if needed? 
  // Ideally callData should have callType. If not, we might mislabel unless we pass context.
  // Ideally we should pass "callType" default based on trigger.
  let callType = callData.callType || "VIDEO";

  // Correction: If triggered from 'chats', force type to 'CHAT' if not set
  if (event.document.includes("chats/")) callType = "CHAT";
  if (event.document.includes("audioCalls/")) callType = "AUDIO";

  if (!receiverId) return null;

  try {
    const userDoc = await admin.firestore().collection("users").doc(receiverId).get();
    if (!userDoc.exists) return null;
    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) return null;

    let advisorAvatar = "";
    let fetchedAdvisorName = "";
    if (advisorId) {
      try {
        const advisorDoc = await admin.firestore().collection("advisors").doc(advisorId).get();
        if (advisorDoc.exists) {
          const data = advisorDoc.data();
          if (data.basicInfo) {
            advisorAvatar = data.basicInfo.profileImage || "";
            fetchedAdvisorName = data.basicInfo.name || "";
          } else {
            advisorAvatar = data.profileImage || data.profileimage || "";
            fetchedAdvisorName = data.name || data.advisorName || "";
          }
        }
      } catch (e) { }
    }

    const finalCallerName = fetchedAdvisorName || callerName;

    // Notification Body/Title Customization
    let notifTitle = finalCallerName;
    let notifBody = "Incoming Call...";
    if (callType === "CHAT") {
      notifBody = "New Chat Request";
    }

    const payload = {
      token: fcmToken,
      // Notification block ensures it pops up even if data-only isn't handled perfectly by all OS versions
      notification: {
        title: notifTitle,
        body: notifBody
      },
      data: {
        type: "call", // Keep "call" type for general "Incoming Request" handling in app, or change to "chat" if app expects it
        callId: callId,
        channelName: channelName,
        title: finalCallerName,
        advisorName: finalCallerName,
        advisorAvatar: advisorAvatar,
        advisorId: advisorId,
        callType: callType,
        click_action: "FLUTTER_NOTIFICATION_CLICK"
      },
      android: { priority: "high", ttl: 0, notification: { channelId: "incoming_calls", priority: "high", sound: "default" } },
      apns: { payload: { aps: { contentAvailable: true, priority: "10" } } }
    };
    await admin.messaging().send(payload);
    console.log(`✅ ${callType} Notification sent to User`);
    return { success: true };
  } catch (error) {
    console.error(`❌ Error sending ${callType} notification:`, error);
    return null;
  }
};

// Triggers for Video, Audio, and Chat
exports.sendIncomingVideoCallNotification = onDocumentCreated("videoCalls/{callId}", handleIncomingCallNotification);
exports.sendIncomingAudioCallNotification = onDocumentCreated("audioCalls/{callId}", handleIncomingCallNotification);
exports.sendIncomingChatNotification = onDocumentCreated("chats/{callId}", handleIncomingCallNotification);

// Function 3: Notify User when Booking Accepted/Rejected (NEW)
exports.notifyUserOnBookingUpdate = onDocumentUpdated(
  "instant_bookings/{bookingId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    const bookingId = event.params.bookingId;

    const oldStatus = before.bookingStatus;
    const newStatus = after.bookingStatus;

    // Only react to specific status changes
    if (oldStatus === newStatus) return null;
    if (newStatus !== "accepted" && newStatus !== "rejected" && newStatus !== "cancelled") return null;

    const userId = after.userId || after.studentId;
    const advisorName = after.advisorName || "Advisor";

    if (!userId) return null;

    console.log(`ℹ️ Booking ${bookingId} status changed: ${oldStatus} -> ${newStatus}`);

    try {
      const userDoc = await admin.firestore().collection("users").doc(userId).get();
      if (!userDoc.exists) return null;
      const fcmToken = userDoc.data().fcmToken;
      if (!fcmToken) {
        console.log(`⚠️ No FCM token for User ${userId}`);
        return null;
      }

      let title = "Booking Update 📅";
      let body = `Your booking status has updated to ${newStatus}.`;

      if (newStatus === "accepted") {
        title = "Booking Confirmed! ✅";
        body = `${advisorName} accepted your session request.`;
      } else if (newStatus === "rejected") {
        title = "Booking Declined ❌";
        body = `${advisorName} is currently unavailable.`;
      } else if (newStatus === "cancelled") {
        title = "Booking Cancelled 🚫";
        body = `The session with ${advisorName} was cancelled.`;
      }

      const payload = {
        token: fcmToken,
        notification: {
          title: title,
          body: body
        },
        data: {
          type: "booking_update",
          bookingId: bookingId,
          status: newStatus,
          click_action: "FLUTTER_NOTIFICATION_CLICK"
        },
        android: { priority: "high" }
      };

      await admin.messaging().send(payload);
      console.log(`✅ Status Update Notification sent to User (${newStatus})`);
      return { success: true };

    } catch (error) {
      console.error("❌ Error notifying user:", error);
      return null;
    }
  }
);

// ==========================================
// 2. PAYMENT PROCESSING FUNCTIONS (HYBRID)
// ==========================================

const handlePaymentProcessing = async (event, collectionName) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  const callId = event.params.callId || event.params.docId;

  // 1. Only Trigger on End (When status changes TO "ended")
  if (before.status === "ended" || after.status !== "ended") {
    return null;
  }

  // Prevent double-processing if we already processed this specific event ID (idempotency)
  // (Optional but good practice)

  console.log(`💰 [${collectionName}] Processing Payment for Call: ${callId}`);

  const bookingId = after.bookingId;
  const duration = after.duration || 0;
  const callEndTime = after.endTime || admin.firestore.FieldValue.serverTimestamp();

  if (!bookingId) {
    console.error("❌ bookingId missing in Call Document. Cannot process payment.");
    return null;
  }

  const db = admin.firestore();

  try {
    await db.runTransaction(async (transaction) => {
      // 2. Fetch Booking (Try Scheduled FIRST if intended as such, or check both)
      // Optimized: user likely knows if it's scheduled. But simplified: check both.

      let bookingRef = db.collection("scheduled_bookings").doc(bookingId);
      let bookingDoc = await transaction.get(bookingRef);
      let isInstant = false;

      if (!bookingDoc.exists) {
        bookingRef = db.collection("instant_bookings").doc(bookingId);
        bookingDoc = await transaction.get(bookingRef);
        isInstant = true;

        if (!bookingDoc.exists) {
          console.error("❌ Booking Doc not found in either collection for ID:", bookingId);
          // Fallback: If no booking found, we can't deduct correctly without Rate. 
          // Abort or try to use Call Data?
          // Let's try to use Call Data if strictly needed, but it's risky.
          return;
        }
      }

      const bookingData = bookingDoc.data();

      // ✅ Check if already processed
      if (bookingData.paymentStatus === "paid" && bookingData.bookingStatus === "completed" && !isInstant) {
        // Scheduled & Paid & Completed -> Likely done.
        // BUT user logic says "past.. still cant deduct".
        // We will only skip if we are SURE it was paid.
        console.log("ℹ️ Booking already marked completed/paid. Checking context...");
        // If it's scheduled and paid, we shouldn't zero it out, but we shouldn't double charge.
        if (bookingData.totalPrice > 0) {
          console.log("⚠️ Payment appears done. Exiting transaction.");
          return;
        }
      }

      const userId = bookingData.userId || bookingData.studentId;
      const advisorId = bookingData.advisorId;

      // 3. Determine Pricing Logic
      let finalCost = 0;
      let shouldDeduct = true; // Default to TRUE for this "Redefined Logic"
      let deductionDescription = "";

      if (isInstant) {
        // PAY PER MINUTE
        const rate = after.ratePerMinute || bookingData.ratePerMinute || 0;
        if (rate <= 0) {
          console.warn("⚠️ Rate is 0 for Instant Call. Charging 0.");
        }
        finalCost = (duration / 60.0) * rate;
        deductionDescription = `Instant Call (${Math.floor(duration / 60)}m ${Math.floor(duration % 60)}s)`;
      } else {
        // SCHEDULED (Fixed Session)
        // Logic: Deduct "One Time" (sessionAmount)
        finalCost = bookingData.sessionAmount || 0;
        deductionDescription = "Scheduled Session Fee";

        // Safety: If pre-paid logic exists, check it.
        // If 'paymentStatus' is ALREADY 'paid', assume it was pre-paid.
        if (bookingData.paymentStatus === "paid") {
          console.log("ℹ️ Scheduled booking marked as PAID. Skipping deduction.");
          shouldDeduct = false;
        }
      }

      // Round to 2 decimals
      finalCost = Math.round(finalCost * 100) / 100;

      console.log(`🧾 Bill: ${deductionDescription} | Cost: ${finalCost} | Deduct: ${shouldDeduct}`);

      // 4. Process Deduction
      let paymentSuccess = false;
      let failureReason = "";

      if (shouldDeduct && finalCost > 0) {
        const walletRef = db.collection("wallets").doc(userId);
        const walletDoc = await transaction.get(walletRef);

        if (!walletDoc.exists) {
          failureReason = "Wallet not found";
          console.error(`❌ ${failureReason} for user ${userId}`);
        } else {
          const currentBalance = walletDoc.data().balance || 0; // Correct field name check
          // Note: field might be 'walletBalance' or 'balance'. Check logic?
          // The file viewed used 'balance' in update, but 'walletBalance' in fetch in Android.
          // Let's safe check both or use consistent 'balance' as per existing index.js logic.
          // Existing index.js used 'balance'.

          if (currentBalance >= finalCost) {
            const newBalance = currentBalance - finalCost;
            transaction.update(walletRef, {
              balance: newBalance,
              walletBalance: newBalance, // Sync both fields just in case
              totalSpent: admin.firestore.FieldValue.increment(finalCost),
              transactionCount: admin.firestore.FieldValue.increment(1),
              lastTransactionTime: admin.firestore.FieldValue.serverTimestamp()
            });

            // Transaction Record (Root Collection for App Query)
            const txnRef = db.collection("transactions").doc();
            transaction.set(txnRef, {
              userId: userId, // 🚨 REQUIRED for App Listener
              amount: finalCost,
              type: "DEBIT",
              category: "call_payment", // Helps App identify type
              status: "success",
              description: deductionDescription,
              timestamp: admin.firestore.FieldValue.serverTimestamp(),
              bookingId: bookingId,
              callId: callId,
              userRole: "student" // Optional context
            });

            paymentSuccess = true;
          } else {
            failureReason = "Insufficient Funds";
            console.warn(`⚠️ ${failureReason}: ${currentBalance} < ${finalCost}`);
          }
        }
      } else if (!shouldDeduct) {
        paymentSuccess = true; // Treated as success if no deduction needed (Pre-paid)
      } else {
        // Cost is 0
        paymentSuccess = true;
      }

      // 5. Update Booking & Call Status
      if (paymentSuccess) {
        transaction.update(bookingRef, {
          bookingStatus: "completed",
          paymentStatus: "paid",
          totalPrice: finalCost,
          actualDuration: duration
        });

        // Credit Advisor
        if (advisorId && finalCost > 0) {
          const advisorRef = db.collection("advisors").doc(advisorId);
          transaction.update(advisorRef, {
            "earningsInfo.totalLifetimeEarnings": admin.firestore.FieldValue.increment(finalCost),
            "earningsInfo.todayEarnings": admin.firestore.FieldValue.increment(finalCost),
            "earningsInfo.pendingBalance": admin.firestore.FieldValue.increment(finalCost)
          });

          // Advisor Txn
          const advTxnRef = advisorRef.collection("transactions").doc();
          transaction.set(advTxnRef, {
            amount: finalCost,
            type: "CREDIT",
            status: "success",
            description: deductionDescription,
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            bookingId: bookingId
          });
        }

      } else {
        // Failed Deduction
        console.warn(`⚠️ Payment Failed for ${bookingId}. Reason: ${failureReason}`);

        transaction.update(bookingRef, {
          bookingStatus: "payment_failed",
          paymentStatus: "failed",
          failureReason: failureReason
        });

        // 🚨 NEW: Create Transaction Record for User (So they see the failure)
        const txnRef = db.collection("transactions").doc();
        transaction.set(txnRef, {
          userId: userId,
          amount: finalCost,
          type: "DEBIT",
          category: "call_payment",
          status: "failed", // User sees "Failed"
          description: `Payment Failed: ${deductionDescription}`,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          bookingId: bookingId,
          callId: callId,
          userRole: "student"
        });

        // 🚨 NEW: Create Transaction Record for Advisor (So they see 'Pending')
        if (advisorId && finalCost > 0) {
          const advisorRef = db.collection("advisors").doc(advisorId);
          const advTxnRef = advisorRef.collection("transactions").doc();
          transaction.set(advTxnRef, {
            amount: finalCost,
            type: "CREDIT",
            status: "pending", // Advisor sees "Pending" - they know money is owed
            description: `Payment Pending: ${deductionDescription}`,
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            bookingId: bookingId
          });
          // Note: We do NOT increment earningsInfo because the money wasn't collected.
        }
      }

      // 6. Signal Completion to Client
      const processedRef = db.collection("processed_transactions").doc(`${bookingId}_completion`);
      transaction.set(processedRef, {
        bookingId: bookingId,
        status: paymentSuccess ? "paid" : "failed",
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        processedBy: "cloud_function_redefined"
      });

    });

    console.log(`✅ Transaction Finished for ${bookingId}`);

  } catch (e) {
    console.error(`❌ Transaction ERROR for ${bookingId}:`, e);
  }
};

exports.processVideoPayment = onDocumentUpdated("videoCalls/{callId}", (event) => handlePaymentProcessing(event, "VideoCall"));
exports.processAudioPayment = onDocumentUpdated("audioCalls/{callId}", (event) => handlePaymentProcessing(event, "AudioCall"));
exports.processChatPayment = onDocumentUpdated("chats/{callId}", (event) => handlePaymentProcessing(event, "Chat")); 
