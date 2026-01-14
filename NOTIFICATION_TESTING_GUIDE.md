# 🚀 Firebase Functions Redeployed - Testing Guide

**Date**: November 27, 2025  
**Time**: 03:02 IST  
**Status**: ✅ **SUCCESSFULLY DEPLOYED**

---

## ✅ Deployment Status

Both functions have been successfully redeployed:

| Function | Status | Version | Region |
|----------|--------|---------|--------|
| `sendBookingNotification` | ✅ Active | v2 (2nd Gen) | us-central1 |
| `sendCallNotification` | ✅ Active | v2 (2nd Gen) | us-central1 |

**Exit Code**: 0 (Success)

---

## 🧪 How to Test Notifications

### Test 1: Call Notification (Video Call)

**Step 1**: Get User UID
- Login to Associate app (user app)
- Check Firebase Console → Authentication → Users
- Copy the user's UID

**Step 2**: Create Test Call in Firestore
- Go to Firestore Console
- Navigate to `videoCalls` collection
- Click "Add Document"
- Use auto-generated ID
- Add these fields:

```
receiverId: <paste-user-uid-here>
advisorName: "Dr. Test Advisor"
channelName: "test_channel_123"
callerId: "advisor_test_123"
status: "initiated"
```

**Step 3**: Check Results
- **App Open**: Incoming call dialog should appear immediately
- **App Background**: Notification should appear in notification tray
- **App Killed**: Notification should still appear (FCM)

**Expected**: User receives "Incoming Video Call 🎥" notification

---

### Test 2: Booking Notification (For Advisor App)

> **Note**: This notification goes to ADVISOR app, not user app

**Step 1**: Get Advisor UID
- Check Firebase Console → Firestore → `advisors` collection
- Copy an advisor's document ID (UID)

**Step 2**: Create Test Booking in Firestore
- Go to Firestore Console
- Navigate to `bookings` collection
- Click "Add Document"
- Use auto-generated ID
- Add these fields:

```
advisorId: <paste-advisor-uid-here>
studentName: "Test Student"
status: "pending"
```

**Step 3**: Check Results
- Advisor app should receive notification
- Title: "New Session Request 🎯"
- Body: "Test Student wants to connect with you"

**Expected**: Advisor receives booking notification

---

## 🔍 Debugging Steps

### If Notifications Don't Appear:

#### 1. Check FCM Token Exists

**For User (Call Notifications)**:
```
Firestore → users → <user-uid> → fcmToken field
```
- Should have a long string value
- If missing: User needs to login again to save token

**For Advisor (Booking Notifications)**:
```
Firestore → advisors → <advisor-uid> → fcmToken field
```
- Should have a long string value
- If missing: Advisor needs to login again

#### 2. Check Firebase Functions Logs

**Command**:
```bash
firebase functions:log --only sendCallNotification
```

**Look for**:
- ✅ "📞 New Call Initiated: {callId}"
- ✅ "✅ Found FCM token for user: {userId}"
- ✅ "🚀 Call notification sent successfully"

**If you see**:
- ❌ "No receiverId found in call data" → Add receiverId field
- ❌ "User document not found" → Check user UID is correct
- ❌ "No FCM token found" → User needs to login again

#### 3. Check Android Logcat

**Command**:
```bash
adb logcat | findstr "FCMService\|UserHome"
```

**Look for**:
- "Message received from: gcm.googleapis.com"
- "Notification Type: video_call"
- "Handling incoming video call"
- "Incoming call notification displayed"

#### 4. Verify Firestore Document Structure

**videoCalls Document Must Have**:
- `receiverId` (string) - User's UID ✅
- `advisorName` (string) - Advisor's name
- `channelName` (string) - Call channel ID
- `callerId` (string) - Advisor's UID
- `status` (string) - "initiated"

**bookings Document Must Have**:
- `advisorId` (string) - Advisor's UID ✅
- `studentName` (string) - Student's name

---

## 📱 App Requirements

### User App (Associate):
✅ FCM Service implemented (`MyFirebaseMessagingService.kt`)  
✅ Notification channels created (`call_channel`)  
✅ Firestore listener fixed (`receiverId` field)  
✅ Permissions granted (POST_NOTIFICATIONS, etc.)  

### Advisor App:
✅ FCM Service implemented  
✅ Notification channels created (`advisor_session_alerts`)  
✅ FCM token saved to Firestore  

---

## 🎯 Quick Test Checklist

- [ ] User has logged into Associate app
- [ ] User's FCM token is saved in Firestore (`users/{uid}/fcmToken`)
- [ ] Create `videoCalls` document with correct `receiverId`
- [ ] Check if notification appears
- [ ] Check Logcat for FCM messages
- [ ] Check Firebase Functions logs for errors

---

## 💡 Common Issues & Solutions

### Issue 1: "No FCM token found"
**Solution**: 
- User needs to login to the app
- FCM token is saved automatically on login
- Check `MyFirebaseMessagingService.kt` is registered in manifest

### Issue 2: Notification doesn't appear when app is killed
**Solution**:
- Check battery optimization is disabled
- Verify notification permissions granted
- Ensure FCM payload includes `notification` key (already done ✅)

### Issue 3: Firestore listener not detecting calls
**Solution**:
- Verify field name is `receiverId` (fixed ✅)
- Check user is logged in
- Verify `status` field is "initiated"

### Issue 4: Function not triggering
**Solution**:
- Verify document is created in correct collection (`videoCalls` or `bookings`)
- Check Firebase Functions logs for errors
- Ensure functions are deployed (done ✅)

---

## 📊 Expected Flow

### Call Notification Flow:
1. Advisor creates document in `videoCalls` collection
2. Firebase Function `sendCallNotification` triggers
3. Function fetches user's FCM token from `users` collection
4. Function sends FCM notification to user
5. User's device receives notification
6. `MyFirebaseMessagingService` handles notification
7. Shows incoming call notification or dialog

### Booking Notification Flow:
1. User creates document in `bookings` collection
2. Firebase Function `sendBookingNotification` triggers
3. Function fetches advisor's FCM token from `advisors` collection
4. Function sends FCM notification to advisor
5. Advisor's device receives notification
6. Shows booking request notification

---

## ✨ Summary

✅ **Functions Deployed**: Both functions active and ready  
✅ **Code Fixed**: Field names aligned (`receiverId`)  
✅ **Ready to Test**: Follow testing guide above  

**Next Step**: Create test documents in Firestore and verify notifications appear!

---

## 🆘 Still Not Working?

If notifications still don't appear after following this guide:

1. **Share Logcat output**:
   ```bash
   adb logcat > logcat.txt
   ```

2. **Share Firebase Functions logs**:
   ```bash
   firebase functions:log > functions_log.txt
   ```

3. **Verify Firestore document structure** - Share screenshot

4. **Check FCM token** - Verify it exists in Firestore

<!-- Updated for repository activity -->
