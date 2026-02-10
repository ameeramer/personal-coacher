package com.personalcoacher.telecom

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

/**
 * Represents an individual connection (call) with the Journal coach.
 * This handles the lifecycle of a single call.
 */
class JournalConnection(
    private val context: Context,
    private val connectionManager: JournalConnectionManager
) : Connection() {

    companion object {
        private const val TAG = "JournalConnection"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isCallActive = false

    init {
        // Set audio properties for voice communication
        audioModeIsVoip = true
    }

    /**
     * Called when the call becomes active.
     * Starts the voice call manager.
     */
    fun onCallActivated() {
        Log.d(TAG, "Call activated")
        isCallActive = true
        connectionManager.onCallStarted(this)
    }

    override fun onAnswer() {
        Log.d(TAG, "Call answered")
        setActive()
        onCallActivated()
    }

    override fun onReject() {
        Log.d(TAG, "Call rejected")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        Log.d(TAG, "Call disconnected by user")
        endCall(DisconnectCause.LOCAL)
    }

    override fun onAbort() {
        Log.d(TAG, "Call aborted")
        endCall(DisconnectCause.CANCELED)
    }

    override fun onHold() {
        Log.d(TAG, "Call put on hold")
        setOnHold()
        connectionManager.onCallHeld()
    }

    override fun onUnhold() {
        Log.d(TAG, "Call resumed from hold")
        setActive()
        connectionManager.onCallResumed()
    }

    override fun onPlayDtmfTone(c: Char) {
        // Not used for Journal calls
    }

    override fun onStopDtmfTone() {
        // Not used for Journal calls
    }

    /**
     * Ends the call and cleans up.
     */
    private fun endCall(disconnectCauseCode: Int) {
        Log.d(TAG, "Ending call with cause: $disconnectCauseCode")
        isCallActive = false

        connectionManager.onCallEnded()

        setDisconnected(DisconnectCause(disconnectCauseCode))
        destroy()
    }

    /**
     * Posts a runnable to execute after a delay.
     */
    fun postDelayed(runnable: Runnable, delayMs: Long) {
        handler.postDelayed(runnable, delayMs)
    }

    /**
     * Ends the call programmatically (e.g., from the UI).
     */
    fun endCallFromUi() {
        endCall(DisconnectCause.LOCAL)
    }
}
