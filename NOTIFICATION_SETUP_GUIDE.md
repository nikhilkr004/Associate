# Notification System - Complete Setup Guide

## ✅ What Was Fixed

### 1. **Missing Firebase Cloud Messaging Service**
   - **Created**: `MyFirebaseMessagingService.kt`
   - **Location**: `app/src/main/java/com/example/associate/NotificationFCM/`
   - **Purpose**: Receives push notifications from Firebase and displays them to users

### 2. **Updated AndroidManifest.xml**
   - **Added**: FCM Service registration
   - **Ensures**: The service can receive Firebase messages

### 3. **Notification Flow**
   ```
   Student Books Session
         ↓
   NotificationManager creates record in Firestore
         ↓
   Firebase Cloud Function detects new record
         ↓
   Function sends FCM notification to Advisor
         ↓
   MyFirebaseMessagingService receives notification
         ↓
   Notification displayed to Advisor
   ```

---

## 🔧 Required Steps to Make Notifications Work

### Step 1: Deploy Firebase Cloud Functions

The Firebase Cloud Function (`sendBookingNotification`) needs to be deployed to work.

**Option A: Using Firebase CLI (Recommended)**
```bash
# Navigate to project root
cd C:\Users\asus\StudioProjects\Associate

# Login to Firebase (if not already logged in)
firebase login

# Deploy the function
firebase deploy --only functions
```

**Option B: Using npx (if Firebase CLI not installed globally)**
```powershell
# First, enable script execution (run PowerShell as Administrator)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# Then deploy
cd C:\Users\asus\StudioProjects\Associate
npx firebase-tools deploy --only functions
```

**Option C: Using Firebase Console**
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Navigate to **Functions** section
4. Upload the `firebase_functions/index.js` file manually

---

### Step 2: Verify Firebase Configuration

**Check these files exist:**
- ✅ `app/google-services.json` - Firebase Android configuration
- ✅ `firebase_functions/index.js` - Cloud function code
- ✅ `firebase.json` - Firebase project configuration

**Verify Firestore Rules:**
Make sure your Firestore rules allow writing to the `notifications` collection:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /notifications/{notificationId} {
      allow read, write: if request.auth != null;
    }
    match /advisors/{advisorId} {
      allow read, write: if request.auth != null;
    }
    match /instant_bookings/{bookingId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

### Step 3: Test FCM Token Generation

**In your app, verify FCM tokens are being saved:**

1. **Check Logs** when a user logs in or registers:
   ```
   Look for: "FCM Token saved: [token]"
   ```

2. **Check Firestore Console**:
   - Go to Firebase Console → Firestore Database
   - Open `users` collection
   - Verify each user document has an `fcmToken` field
   - Open `advisors` collection
   - Verify each advisor document has an `fcmToken` field

---

### Step 4: Test Notification Flow

**Manual Test:**

1. **Create a test notification in Firestore Console:**
   - Go to Firebase Console → Firestore Database
   - Create a new document in `notifications` collection with:
   ```json
   {
     "advisorId": "[ADVISOR_USER_ID]",
     "studentName": "Test Student",
     "bookingId": "TEST123",
     "advisorName": "Test Advisor",
     "type": "instant_booking",
     "title": "Test Notification",
     "message": "This is a test notification",
     "timestamp": [current timestamp],
     "isRead": false,
     "notificationSeen": false
   }
   ```

2. **Check if notification appears** on the advisor's device

3. **Check Firebase Functions Logs:**
   ```bash
   firebase functions:log
   ```
   Look for:
   - "New notification for Advisor: [advisorId]"
   - "Successfully sent message: [response]"

---

## 🐛 Troubleshooting

### Issue 1: Notifications Not Appearing

**Possible Causes:**
1. ❌ Firebase Cloud Function not deployed
2. ❌ FCM token not saved in Firestore
3. ❌ Notification permissions not granted
4. ❌ App in background/killed

**Solutions:**
1. Deploy Firebase Functions (see Step 1)
2. Check Firestore for `fcmToken` field in user/advisor documents
3. Request notification permission in app:
   ```kotlin
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
       ActivityCompat.requestPermissions(
           this,
           arrayOf(Manifest.permission.POST_NOTIFICATIONS),
           REQUEST_CODE_NOTIFICATIONS
       )
   }
   ```
4. Test with app in foreground first

---

### Issue 2: "Advisor has no FCM Token"

**Solution:**
- Ensure advisor logs in at least once after the FCM service is added
- FCM token is generated on app start and saved to Firestore
- Check `PersonalScreenActivity.kt` - it saves FCM token on user registration
- For existing users, they need to log out and log back in

---

### Issue 3: Firebase Function Fails

**Check Firebase Console Logs:**
```bash
npx firebase-tools functions:log
```

**Common Errors:**
- "Advisor document not found" → Advisor ID is incorrect
- "Advisor has no FCM Token" → Token not saved in Firestore
- "Permission denied" → Check Firestore security rules

---

## 📱 Testing Checklist

### Before Testing:
- [ ] Firebase Cloud Functions deployed
- [ ] App rebuilt and installed on device
- [ ] Notification permissions granted
- [ ] User/Advisor logged in (to generate FCM token)

### Test Scenarios:

#### Test 1: New User Registration
1. Register a new user
2. Check logs for "FCM Token saved"
3. Verify token in Firestore

#### Test 2: Instant Booking Notification
1. Student creates instant booking
2. Check Firestore for new document in `notifications` collection
3. Advisor should receive notification
4. Check Firebase Functions logs

#### Test 3: Manual Notification
1. Create notification document in Firestore manually
2. Verify notification appears on device
3. Check notification channel is created

---

## 🔍 Debugging Commands

### View Firebase Functions Logs:
```bash
npx firebase-tools functions:log --limit 50
```

### Test Firebase Function Locally:
```bash
cd firebase_functions
npm install
npx firebase-tools emulators:start --only functions
```

### Check Firestore Data:
```bash
npx firebase-tools firestore:indexes
```

---

## 📝 Important Notes

1. **Notification Channel**: The app uses `user_calls_channel` - this is created automatically by `MyFirebaseMessagingService`

2. **FCM Token Refresh**: Tokens can expire/refresh. The `onNewToken()` method in `MyFirebaseMessagingService` handles this automatically.

3. **Background vs Foreground**:
   - **Foreground**: Notification handled by `onMessageReceived()`
   - **Background**: System tray notification shown automatically

4. **Data vs Notification Payload**:
   - Current implementation sends both `notification` and `data` payloads
   - `notification` payload shows system notification
   - `data` payload contains booking details

---

## 🚀 Next Steps

1. **Deploy Firebase Functions** (most critical!)
2. **Test with real devices**
3. **Monitor Firebase Console logs**
4. **Add notification click handling** (optional - already partially implemented)

---

## 📞 Support

If notifications still don't work after following this guide:

1. Check Firebase Console → Functions → Logs
2. Check Android Logcat for FCM-related logs
3. Verify `google-services.json` is up to date
4. Ensure Firebase project has Cloud Functions enabled (Blaze plan required)

---

**Last Updated**: 2025-11-25
**Status**: ✅ Code Complete - Needs Firebase Deployment

<!-- Updated for repository activity -->
