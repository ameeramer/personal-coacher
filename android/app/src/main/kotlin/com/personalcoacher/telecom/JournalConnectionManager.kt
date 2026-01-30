package com.personalcoacher.telecom

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.personalcoacher.R
import com.personalcoacher.voice.VoiceCallManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Journal phone account and call lifecycle.
 * This is the bridge between the Android Telecom framework and the VoiceCallManager.
 */
@Singleton
class JournalConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceCallManager: VoiceCallManager
) {
    companion object {
        private const val TAG = "JournalConnectionManager"
    }

    private val telecomManager: TelecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    private val phoneAccountHandle: PhoneAccountHandle by lazy {
        PhoneAccountHandle(
            ComponentName(context, JournalConnectionService::class.java),
            JournalConnectionService.PHONE_ACCOUNT_ID
        )
    }

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: Flow<Boolean> = _isRegistered.asStateFlow()

    private val _isInCall = MutableStateFlow(false)
    val isInCall: Flow<Boolean> = _isInCall.asStateFlow()

    private var currentConnection: JournalConnection? = null
    private var callScope: CoroutineScope? = null

    /**
     * Registers the Journal phone account with the Telecom framework.
     * This must be called before starting any calls.
     */
    fun registerPhoneAccount() {
        try {
            val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "Journal Coach")
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED or
                    PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS
                )
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setShortDescription("Voice journaling with AI coach")
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
                .build()

            telecomManager.registerPhoneAccount(phoneAccount)
            _isRegistered.value = true
            Log.d(TAG, "Journal phone account registered")

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for registering phone account", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register phone account", e)
        }
    }

    /**
     * Unregisters the Journal phone account.
     */
    fun unregisterPhoneAccount() {
        try {
            telecomManager.unregisterPhoneAccount(phoneAccountHandle)
            _isRegistered.value = false
            Log.d(TAG, "Journal phone account unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister phone account", e)
        }
    }

    /**
     * Starts a call to the Journal.
     * This triggers the native call UI.
     */
    fun startCall() {
        if (_isInCall.value) {
            Log.w(TAG, "Already in a call")
            return
        }

        try {
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }

            telecomManager.placeCall(
                Uri.parse(JournalConnectionService.JOURNAL_PHONE_URI),
                extras
            )

            Log.d(TAG, "Call initiated")

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for placing call", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call", e)
        }
    }

    /**
     * Called when a call connection is established and becomes active.
     */
    internal fun onCallStarted(connection: JournalConnection) {
        Log.d(TAG, "Call started")
        currentConnection = connection
        _isInCall.value = true

        callScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Start the voice call manager
        callScope?.launch {
            voiceCallManager.startCall()
        }
    }

    /**
     * Called when the call is put on hold.
     */
    internal fun onCallHeld() {
        Log.d(TAG, "Call held")
        // Pause the voice call manager
        // TODO: Implement pause functionality in VoiceCallManager
    }

    /**
     * Called when the call is resumed from hold.
     */
    internal fun onCallResumed() {
        Log.d(TAG, "Call resumed")
        // Resume the voice call manager
        // TODO: Implement resume functionality in VoiceCallManager
    }

    /**
     * Called when the call ends.
     */
    internal fun onCallEnded() {
        Log.d(TAG, "Call ended")
        currentConnection = null
        _isInCall.value = false

        // End the voice call and generate journal entry
        callScope?.launch {
            voiceCallManager.endCall()
            callScope?.cancel()
            callScope = null
        }
    }

    /**
     * Ends the current call programmatically.
     */
    fun endCall() {
        currentConnection?.endCallFromUi()
    }

    /**
     * Checks if the app has permission to manage its own calls.
     */
    fun hasCallPermission(): Boolean {
        return try {
            // Check if we can access the telecom manager
            telecomManager.phoneAccountsSupportingScheme(PhoneAccount.SCHEME_TEL)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
