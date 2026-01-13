# Audio/Video Call & Wallet System Documentation

This document outlines the technical flow of the Audio/Video calling system and the wallet transaction mechanism in the Associate App.

## 1. Overview
The system allows users to connect with advisors via high-quality audio or video calls. It features a real-time visual cost tracker during the call and processes a secure, atomic financial transaction upon call completion.

---

## 2. Call System Architecture

### Technologies Used
*   **Video/Audio Engine**: ZegoCloud (via `ZegoCallManager`)
*   **Backend**: Firebase Firestore (Data & Transactions)
*   **Authentication**: Firebase Auth

### Call Lifecycle Flow

1.  **Initiation**:
    *   The call starts in `VideoCallActivity.kt` or `AudioCallActivity.kt`.
    *   **Permissions**: The app checks for Camera and Microphone permissions. If active, it proceeds.
    *   **Zego Initialization**: The app initializes the Zego engine using the App ID and Sign.
    *   **Room Joining**: The user joins a specific "Room" (Channel ID) defined by the `bookingId` or a unique identifier.

2.  **During Call**:
    *   **UI Updates**: A timer displays the duration.
    *   **Visual Cost Tracker**:
        *   A local handler (`visualTrackerHandler`) runs every **1 second**.
        *   It calculates the estimated cost: `(Elapsed Seconds / 60) * Rate per Minute`.
        *   It updates the "Spent Amount" text on the screen for user awareness.
        *   **Balance Check**: It compares the current estimated cost against the user's fetched `walletBalance`. If funds run out, an "Insufficient Balance" alert is triggered.
    *   **Picture-in-Picture (PiP)**:
        *   If the user presses Home, `onUserLeaveHint()` triggers.
        *   The activity enters PiP mode.
        *   `onPictureInPictureModeChanged()` hides unnecessary controls (buttons, text) and keeps only the video/avatar visible.

3.  **Termination**:
    *   The call ends when:
        *   User clicks "End Call".
        *   Advisor leaves the room (detected via `onRoomUserUpdate`).
        *   Balance is exhausted.
    *   The app leaves the Zego room (`zegoManager.leaveRoom()`).
    *   The transaction process begins.

---

## 3. Wallet & Transaction System

The financial transaction is **not** continuous (per second) but is processed as a **single atomic transaction** at the end of the call to ensure data integrity and prevent partial failures.

### The Transaction Flow (`BookingRepository.kt`)

When `endCall()` is triggered, the app calls `completeBookingWithTransaction(...)`. This executes a **Firestore Atomic Transaction**:

1.  **Read Phase (Locking Data)**:
    *   The system reads the current state of:
        *   **User Wallet**: To get the latest balance.
        *   **Advisor Profile**: To get current earnings stats.
        *   **Booking Document**: To verify booking validity.

2.  **Calculation Phase**:
    *   **Duration**: Calculated as `(Call End Time - Call Start Time)`.
    *   **Cost**:
        *   *Instant Booking*: `(Duration in Minutes) * (Advisor's Rate per Minute)`
        *   *Scheduled Booking*: Fixed Session Fee.
    *   The final cost is rounded to 2 decimal places.

3.  **Execution Phase (All-or-Nothing)**:
    *   **User Debit**: The calculated cost is subtracted from the User's Wallet Balance.
        *   `balance = balance - cost`
        *   `totalSpent` and `transactionCount` are incremented.
    *   **Advisor Credit**: The calculated cost is added to the Advisor's Earnings.
        *   `todayEarnings = todayEarnings + cost`
        *   `totalLifetimeEarnings = totalLifetimeEarnings + cost`
    *   **Booking Update**:
        *   Booking status set to `completed`.
        *   Payment status set to `paid`.
    *   **History Logs**:
        *   A "DEBIT" transaction record is created in the User's `transactions` sub-collection.
        *   A "CREDIT" transaction record is created in the Advisor's `transactions` sub-collection.

4.  **Completion**:
    *   If any step fails (e.g., network loss during the split second of commit), the **entire** transaction is rolled back. No money is lost or gained incorrectly.
    *   On success, the user is navigated back to the Home Screen.

---

## 4. Key Files
*   **`VideoCallActivity.kt` / `AudioCallActivity.kt`**: Handles UI, Zego logic, visual tracking, and triggers the final transaction.
*   **`BookingRepository.kt`**: Contains the `completeBookingWithTransaction` function that performs the secure Firestore transaction.
*   **`WalletRepository.kt`**: Helper functions for reading/writing wallet data.
