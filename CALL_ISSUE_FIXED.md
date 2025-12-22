# 🔧 Call and Notification Issues - FIXED

**Date**: November 27, 2025  
**Status**: ✅ **CRITICAL BUG FIXED**

---

## 🐛 Problem Identified

### Issue 1: Incoming Calls Not Working
**Root Cause**: Field name mismatch between Firebase Function and Android App

| Component | Field Name Used | Location |
|-----------|----------------|----------|
| Firebase Function | `receiverId` | `firebase_functions/index.js` line 96 |
| Android App | `userId` | `HomeFragment.kt` line 222 |

**Result**: Firestore listener never matched incoming calls because it was looking for wrong field name.

---

## ✅ Fix Applied

### File: `HomeFragment.kt` (Line 222)

**BEFORE**:
```kotlin
.whereEqualTo("userId", currentUserId)
```

**AFTER**:
```kotlin
.whereEqualTo("receiverId", currentUserId)  // ✅ FIXED
```

**Impact**: Now Firestore listener will correctly detect incoming calls when app is open.

---

## 📋 About Booking Notifications

### Clarification:
**This is the USER-SIDE app** (Associate app - for users/students)

**Booking notifications should go to ADVISOR-SIDE app**, not this app.

| Notification Type | Recipient | App |
|-------------------|-----------|-----|
| Booking Request | Advisor | Advisor App |
| Incoming Call | User | Associate App (this app) ✅ |

**Conclusion**: Booking notifications NOT appearing in this app is **EXPECTED BEHAVIOR**, not a bug.

---

## 🧪 How to Test Incoming Calls

### Test 1: With App Open (Foreground)

**Steps**:
1. Open Associate app and login as a user
2. Note the user's UID from Logcat or Firebase Console
3. Go to Firestore Console
4. Create a new document in `videoCalls` collection:

```json
{
  "receiverId": "<user-uid-here>",
  "advisorName": "Dr. Test Advisor",
  "channelName": "test_channel_123",
  "callerId": "advisor_test_id",
  "status": "initiated"
}
```

5. **Expected Result**: 
   - Incoming call dialog should appear immediately
   - Shows advisor name "Dr. Test Advisor"
   - Has Accept/Decline buttons

**Pass Criteria**: Dialog appears within 2 seconds

---

### Test 2: With App in Background

**Steps**:
1. Open Associate app and login
2. Press Home button (app goes to background)
3. Create `videoCalls` document as above
4. **Expected Result**:
   - Notification appears in notification tray
   - Shows "Incoming Video Call 🎥"
   - Shows advisor name
   - Has Accept/Decline action buttons

**Pass Criteria**: Notification visible in notification drawer

---

### Test 3: With App Completely Closed/Killed ⭐

**Steps**:
1. Force stop the app:
   - Settings → Apps → Associate → Force Stop
2. Create `videoCalls` document as above
3. **Expected Result**:
   - FCM notification appears even though app is killed
   - Shows "Incoming Video Call 🎥"
   - Tapping notification opens app to incoming call screen

**Pass Criteria**: Notification appears within 5 seconds

---

## 🔍 Debugging Checklist

If calls still don't work, check these:

### 1. Verify FCM Token is Saved
- Open Firestore Console
- Go to `users` collection
- Find your user document
- Check if `fcmToken` field exists and has a value
- **If missing**: FCM token not being saved on login

### 2. Check Firebase Function Logs
```bash
firebase functions:log --only sendCallNotification
```

**Look for**:
- "📞 New Call Initiated: {callId}"
- "✅ Found FCM token for user: {userId}"
- "🚀 Call notification sent successfully"

**If you see errors**: Function is failing to send notification

### 3. Check Android Logcat
```bash
adb logcat | findstr "UserHome\|FCMService"
```

**Look for**:
- "Starting to listen for incoming calls for user: {uid}"
- "Received X call documents"
- "Incoming call detected"
- "Message received from: gcm.googleapis.com"

### 4. Verify Notification Permissions
- Android 13+: Check POST_NOTIFICATIONS permission granted
- Settings → Apps → Associate → Notifications → Enabled

### 5. Check Battery Optimization
- Settings → Apps → Associate → Battery
- Set to "Unrestricted"

---

## 📱 How Call Reception Works

### Two Mechanisms (Both Active):

#### 1. Firestore Listener (When App is Open)
- **File**: `HomeFragment.kt` lines 218-245
- **Trigger**: Real-time listener on `videoCalls` collection
- **Advantage**: Instant response (< 1 second)
- **Limitation**: Only works when app is open

#### 2. FCM Notifications (Always Works)
- **File**: `MyFirebaseMessagingService.kt`
- **Trigger**: Firebase Cloud Function sends FCM message
- **Advantage**: Works even when app is killed
- **Limitation**: Slightly slower (2-5 seconds)

**Best Practice**: Keep both for optimal UX

---

## 🎯 Summary

### What Was Fixed:
✅ Changed `userId` to `receiverId` in HomeFragment.kt  
✅ Now matches Firebase Function field name  
✅ Firestore listener will detect incoming calls  

### What's Expected Behavior:
ℹ️ Booking notifications go to Advisor app, not this app  
ℹ️ This is USER-SIDE app - receives calls, doesn't get booking notifications  

### Next Steps:
1. **Test with app open** - Create videoCalls document manually
2. **Test with app closed** - Verify FCM notifications work
3. **Check Logcat** - Verify listener is working
4. **Verify FCM token** - Ensure it's saved in Firestore

---

## 📝 Files Modified

| File | Change | Line |
|------|--------|------|
| `HomeFragment.kt` | `userId` → `receiverId` | 222 |

---

## ✨ Expected Behavior After Fix

1. **App Open**: Incoming call dialog appears immediately via Firestore listener
2. **App Background**: Notification appears via FCM
3. **App Killed**: Notification appears via FCM (works because we added notification payload)

All three scenarios should now work correctly! 🎉
