package org.fenixuz.utils

import android.content.Context
import org.telegram.messenger.ApplicationLoader

/**
 * Novagram: which camera a round video note starts recording from — front (default) or rear.
 *
 * Backed by device-only "db" prefs (no Firebase). The value is loaded once and cached in [isFront],
 * so the hot read at InstantCameraView (when the camera opens) is a plain field access — no disk
 * touch per recording. Writes go through the property setter and persist async via apply().
 *
 * It is controlled by a single toggle in FenixSettings; there is no per-recording front/back popup
 * anymore — the saved choice is used every time. The in-recording flip button still switches the
 * live camera, but deliberately does NOT overwrite this preference, so the setting stays the
 * authoritative "start" camera for the next round video.
 */
object CameraSituation {

    private const val PREF = "db"
    private const val KEY = "round_camera_front"

    private val prefs by lazy {
        ApplicationLoader.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    // Default front, matching Telegram's own instant-camera default. The initializer assigns the
    // backing field directly (not through the setter), so loading never re-writes to prefs.
    var isFront: Boolean = prefs.getBoolean(KEY, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(KEY, value).apply()
        }
}
