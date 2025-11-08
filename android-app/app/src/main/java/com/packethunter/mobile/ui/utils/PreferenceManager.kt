package com.packethunter.mobile.ui.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app preferences for user settings and one-time warnings
 */
class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "packet_hunter_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_PAYLOAD_WARNING_SHOWN = "payload_warning_shown"
        private const val KEY_CONSENT_GIVEN = "consent_given"
    }
    
    /**
     * Check if payload warning has been shown
     */
    fun hasSeenPayloadWarning(): Boolean {
        return prefs.getBoolean(KEY_PAYLOAD_WARNING_SHOWN, false)
    }
    
    /**
     * Mark payload warning as shown
     */
    fun setPayloadWarningShown() {
        prefs.edit().putBoolean(KEY_PAYLOAD_WARNING_SHOWN, true).apply()
    }
    
    /**
     * Check if user has given consent
     */
    fun hasGivenConsent(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_GIVEN, false)
    }
    
    /**
     * Set consent given
     */
    fun setConsentGiven(given: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_GIVEN, given).apply()
    }
}

