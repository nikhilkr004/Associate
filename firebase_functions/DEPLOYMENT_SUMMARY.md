# Firebase Functions Deployment Summary

**Date**: November 27, 2025  
**Project**: new-e70d7  
**Account**: kumarnikhil729292@gmail.com  
**Status**: ✅ **SUCCESSFULLY DEPLOYED**

---

## Deployed Functions

### 1. sendBookingNotification
- **Trigger**: Firestore document created in `notifications/{notificationId}`
- **Target**: Advisors
- **Purpose**: Sends push notifications to advisors when users book sessions
- **Region**: us-central1
- **Runtime**: Node.js 20 (2nd Gen)

### 2. sendIncomingCallNotification
- **Trigger**: Firestore document created in `videoCalls/{callId}`
- **Target**: Users
- **Purpose**: Sends high-priority call notifications to users when advisors initiate calls
- **Region**: us-central1
- **Runtime**: Node.js 20 (2nd Gen)

---

## Deployment Details

**Command**: `firebase deploy --only functions`

**Result**: Both functions deployed successfully to Firebase project `new-e70d7`

**Exit Code**: 0 (Success)

---

## Function Configuration

### sendBookingNotification
```javascript
Trigger: onDocumentCreated("notifications/{notificationId}")
Collection: advisors (for FCM token lookup)
Notification Type: Standard FCM notification with data payload
Priority: High
Channel: user_calls_channel
```

### sendIncomingCallNotification
```javascript
Trigger: onDocumentCreated("videoCalls/{callId}")
Collection: users (for FCM token lookup)
Notification Type: Data-only message (no notification payload)
Priority: High
TTL: 0 (immediate delivery)
```

---

## How to Test

### Test Booking Notification
1. Create a document in Firestore: `notifications/{notificationId}`
2. Include fields: `advisorId`, `title`, `message`, `bookingId`, `type`
3. Advisor app should receive notification

### Test Call Notification
1. Create a document in Firestore: `videoCalls/{callId}`
2. Include fields: `receiverId`, `callerName`, `channelName`
3. User app should receive incoming call notification with Accept/Cancel buttons

---

## Monitoring

View function logs:
```bash
firebase functions:log
```

View specific function logs:
```bash
firebase functions:log --only sendBookingNotification
firebase functions:log --only sendIncomingCallNotification
```

---

## Firebase Console

View deployed functions:
https://console.firebase.google.com/project/new-e70d7/functions

---

## Next Steps

✅ Functions are deployed and active  
✅ Ready to receive triggers from Firestore  
✅ Test both notification flows  
✅ Monitor logs for any errors  

---

## Notes

- Both functions coexist in the same `index.js` file
- No conflicts between functions (different triggers)
- Existing functions were preserved during deployment
- Functions use Firebase Functions v2 (2nd generation)
