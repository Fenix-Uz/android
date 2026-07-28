package org.fenixuz.utils

import android.content.Context
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update

/**
 * Novagram: global toggle that AUTO-ACCEPTS incoming join requests in channels/groups you manage.
 *
 * Pro shipped a MANUAL per-chat bulk-approver (a dialog where you pick how many to approve, then it loops
 * `hideChatJoinRequest` per user in batches). This is a cleaner, truly-automatic take:
 *  - real-time: hooks the `TL_updatePendingJoinRequests` update (fired to admins when someone requests to
 *    join) and approves in ONE call via `TL_messages_hideAllChatJoinRequests(approved=true)` — no per-user
 *    resolution, no access_hash juggling, no coroutine batch loop. The server clears all pending at once.
 *  - efficient/ANR-safe: [maybeAccept] runs on the update thread; its FIRST line is an O(1) volatile toggle
 *    check, so it costs nothing when the feature is off; the approve call is async (sendRequest).
 *  - safe: only fires where the current user actually has the right (creator or invite_users admin right),
 *    and only when `requests_pending > 0`, so the post-approve update (pending=0) can't loop.
 *
 * Device-only ("db" pref), no Firebase. Global (every chat you administer), matching "auto-accept".
 */
object AutoAcceptJoin {

    private const val PREF = "db"
    private const val KEY = "auto_accept_join"

    @Volatile private var loaded = false
    @Volatile private var enabled = false

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

    fun isEnabled(): Boolean {
        ensureLoaded()
        return enabled
    }

    @Synchronized
    fun toggle(): Boolean {
        ensureLoaded()
        enabled = !enabled
        prefs().edit().putBoolean(KEY, enabled).apply()
        return enabled
    }

    /**
     * Hooked from MessagesController.processUpdateArray for every pending-join-requests update.
     * No-op (one volatile read) when the toggle is off.
     */
    fun maybeAccept(account: Int, update: TL_update.TL_updatePendingJoinRequests) {
        if (!isEnabled()) return
        if (update.requests_pending <= 0) return
        val peer = update.peer ?: return
        val chatId: Long = when (peer) {
            is TLRPC.TL_peerChannel -> peer.channel_id
            is TLRPC.TL_peerChat -> peer.chat_id
            else -> return
        }
        val controller = MessagesController.getInstance(account)
        val chat = controller.getChat(chatId) ?: return
        // Only approve where we have the right to — creator or the invite_users admin right.
        if (!ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE)) return

        val req = TLRPC.TL_messages_hideAllChatJoinRequests()
        req.approved = true
        req.peer = controller.getInputPeer(-chatId)
        ConnectionsManager.getInstance(account).sendRequest(req) { response, _ ->
            if (response is TLRPC.Updates) {
                controller.processUpdates(response, false)
            }
        }
    }
}
