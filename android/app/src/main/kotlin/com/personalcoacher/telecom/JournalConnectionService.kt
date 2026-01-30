package com.personalcoacher.telecom

import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android Telecom ConnectionService for the Journal voice call feature.
 *
 * This service integrates with the Android Telecom framework to provide
 * a native phone call experience:
 * - Works on lock screen
 * - Supports Bluetooth headsets
 * - Shows in native call UI
 * - Proper audio routing
 *
 * Note: This requires MANAGE_OWN_CALLS permission and registration as a PhoneAccount.
 */
@AndroidEntryPoint
class JournalConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "JournalConnectionService"
        const val PHONE_ACCOUNT_ID = "journal_coach_account"
        const val JOURNAL_PHONE_URI = "tel:journal"
    }

    @Inject
    lateinit var journalConnectionManager: JournalConnectionManager

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "Creating outgoing connection to Journal")

        val connection = JournalConnection(applicationContext, journalConnectionManager)

        connection.setConnectionCapabilities(
            Connection.CAPABILITY_MUTE or
            Connection.CAPABILITY_SUPPORT_HOLD or
            Connection.CAPABILITY_HOLD
        )

        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)

        connection.setAddress(
            Uri.parse(JOURNAL_PHONE_URI),
            TelecomManager.PRESENTATION_ALLOWED
        )

        connection.setCallerDisplayName(
            "Journal Coach",
            TelecomManager.PRESENTATION_ALLOWED
        )

        // Start in dialing state, then move to active
        connection.setDialing()

        // Simulate connection delay then activate
        connection.postDelayed({
            connection.setActive()
            connection.onCallActivated()
        }, 1000)

        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "Creating incoming connection from Journal")

        val connection = JournalConnection(applicationContext, journalConnectionManager)

        connection.setConnectionCapabilities(
            Connection.CAPABILITY_MUTE or
            Connection.CAPABILITY_SUPPORT_HOLD or
            Connection.CAPABILITY_HOLD
        )

        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)

        connection.setAddress(
            Uri.parse(JOURNAL_PHONE_URI),
            TelecomManager.PRESENTATION_ALLOWED
        )

        connection.setCallerDisplayName(
            "Journal Coach",
            TelecomManager.PRESENTATION_ALLOWED
        )

        // Set to ringing state for incoming calls
        connection.setRinging()

        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "Failed to create outgoing connection")
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "Failed to create incoming connection")
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }
}
