package org.fenixuz.utils

import android.content.Context
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC

/**
 * Novagram "Block APK files" — when enabled, an INCOMING message whose attachment is an Android
 * package (.apk) is shown as a plain "blocked" notice instead of a downloadable file, so a malicious
 * app can't be installed by a careless tap.
 *
 * Implemented through Telegram's own [MessageObject.isRestrictedMessage] path (the same one used for
 * server-restricted content): the message keeps its place in the conversation but its type collapses to
 * TYPE_TEXT, which removes the document UI, the download button and the media entry everywhere at once
 * (chat, chat-list preview, shared media, PhotoViewer). One chokepoint, no per-surface patching.
 *
 * This is a convenience guard, not cryptographic protection: the user can turn it off and see the file
 * again. Outgoing files are never touched — you know what you sent yourself.
 *
 * Device-only (a single boolean in the shared "db" prefs), cached in a volatile field so the check on the
 * message-building hot path is O(1) with no disk read.
 */
object ApkShield {

    private const val PREF = "db"
    private const val KEY = "apk_shield"
    private const val APK_MIME = "application/vnd.android.package-archive"

    @Volatile
    private var loaded = false
    @Volatile
    private var enabled = false

    private fun prefs() =
        ApplicationLoader.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            enabled = prefs().getBoolean(KEY, false)
            loaded = true
        }
    }

    @JvmStatic
    fun isEnabled(): Boolean {
        ensureLoaded()
        return enabled
    }

    @JvmStatic
    fun setEnabled(value: Boolean) {
        ensureLoaded()
        if (enabled == value) return
        enabled = value
        prefs().edit().putBoolean(KEY, value).apply()   // async, no ANR
    }

    @JvmStatic
    fun toggle() = setEnabled(!isEnabled())

    /**
     * True when [message] is an incoming message carrying an .apk attachment and the shield is on —
     * i.e. the message must be rendered as a "blocked" notice instead of a file.
     *
     * Ordered cheapest-first: the disabled case (most users) returns before touching the media at all.
     * Only a real document attachment counts; a document merely referenced by a link preview, game or
     * story is left alone, since nothing installable is being handed to the user there.
     */
    @JvmStatic
    fun shouldHide(message: TLRPC.Message?): Boolean {
        if (!isEnabled()) return false
        if (message == null || message.out) return false
        val media = MessageObject.getMedia(message) as? TLRPC.TL_messageMediaDocument ?: return false
        return isApk(media.document)
    }

    /**
     * Both signals are checked: the declared mime type AND the file name. A sender can hand over an APK
     * with a generic mime (application/octet-stream), or hide the extension behind a decoy one
     * ("photo.jpg.apk") — the name check catches what the mime check misses.
     */
    private fun isApk(document: TLRPC.Document?): Boolean {
        if (document == null) return false
        if (APK_MIME.equals(document.mime_type, ignoreCase = true)) return true
        val name = FileLoader.getDocumentFileName(document)
        return name != null && name.endsWith(".apk", ignoreCase = true)
    }
}
