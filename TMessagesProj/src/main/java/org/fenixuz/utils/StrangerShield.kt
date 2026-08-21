package org.fenixuz.utils

import android.content.Context
import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Novagram "Protect from strangers" — one-on-one chats with people NOT in your contacts (not bots,
 * not yourself, not Telegram's own official/support accounts) are filed into a separate "Stranger
 * chats" inbox: hidden from the main list, silent (no notification), and excluded from the unread
 * badges.
 *
 * STICKY by design (user request 2026-06-13): a stranger that appears while the shield is ON is
 * CAPTURED into the inbox and STAYS there even after the shield is turned OFF — because it is still a
 * stranger. A NEW stranger that writes while the shield is OFF is NOT captured, so it shows up in the
 * main list normally (capturing only happens while protection is on). If a captured stranger later
 * becomes a contact, [isStranger] turns false and they leave the inbox / reappear in the main list.
 *
 * Because "off" is therefore NOT symmetric with "on", switching the shield OFF must ASK (user feedback
 * 2026-08-13: people turned it off, saw nothing come back and read it as a bug). The settings toggle
 * shows [countCaptured] and offers [releaseCaptured] — "return them to the main list" — or keeping the
 * inbox as it is. Silently emptying the inbox is wrong too: for many users the whole point is that a
 * stranger stays filed away.
 *
 * Switching it back ON asks NOTHING and is a clean re-arm: [clearAllowed] wipes the "Not a stranger"
 * whitelist so every current stranger is filed again, no exceptions. That is not drift — it only happens
 * when the user deliberately turns protection back on, which means "file all strangers" — and it doubles
 * as the undo for a mis-tapped [trust], which is why no re-hide prompt and no reverse menu item exist.
 * So: [trust] lasts until the next re-arm; one question total, on the OFF direction only.
 *
 * The single source of truth is [belongsInInbox] = "a stranger AND (shield on OR captured)". Every
 * surface uses it: the dialog list (hide / inbox), notifications, and both unread badges — so they
 * never disagree (no "badge counts a chat you can't see").
 *
 * EVERYTHING is PER-ACCOUNT — the switch as well as the sets (the sets since 2026-06-15, the switch
 * since 2026-08-13). Dialog ids are account-scoped, so the same id being a stranger in account A must
 * never hide it in account B; and the switch has to match, because a global switch sitting on top of
 * per-account sets is what made "turn it off" reachable for the current account only. It is also the
 * useful shape: strangers writing to a work account is the point, on a personal account it isn't. The
 * sets are self-pruned on every toggle — ids whose user became a contact or whose dialog was deleted
 * are dropped — so they can't grow forever.
 *
 * Clean, optimised rewrite of pro's `MyContact` (which mutated storage on the render hot path with
 * Gson + blocking commit(), filtered in two places with O(N·M) loops, faked a paid-message cost,
 * spammed per-stranger network mutes, and force-enabled unrelated features). Here: a lock-free flag +
 * per-account captured sets, O(1) hot-path reads, async writes, no network, device-only.
 */
object StrangerShield {

    private const val PREF = "db"
    private const val KEY_ENABLED_PREFIX = "stranger_shield_"
    private const val KEY_CAPTURED_PREFIX = "stranger_captured_"
    private const val KEY_ALLOWED_PREFIX = "stranger_allowed_"
    // Old single global set (pre per-account). Migrated into account 0 once, then removed.
    private const val KEY_CAPTURED_LEGACY = "stranger_captured"
    // Old single global switch (pre per-account). NOT migrated-and-deleted but kept forever as the
    // DEFAULT for any account that has no key of its own: eager migration would have to enumerate
    // activated accounts, and [ensureLoaded] can run before they are activated — an account missed
    // there would silently lose its protection and start showing/notifying stranger chats again.
    // As a default it is also right for an account added later: the user asked for protection once.
    private const val KEY_ENABLED_LEGACY = "stranger_shield"

    @Volatile
    private var loaded = false

    // Per-account switch. Read from the UI thread (dialog list), the notifications queue and the storage
    // queue, written from the UI thread — an AtomicIntegerArray (0/1) gives every element the visibility
    // a plain BooleanArray element would NOT have, at the cost of an ordinary volatile read.
    private val enabled = AtomicIntegerArray(UserConfig.MAX_ACCOUNT_COUNT)

    // Per-account inbox membership. Read on the UI thread (dialog list) AND background threads
    // (notifications/badge), written from both → a lock-free concurrent set per account.
    private val captured = Array(UserConfig.MAX_ACCOUNT_COUNT) { ConcurrentHashMap.newKeySet<Long>() }

    // Per-account whitelist: strangers the user explicitly moved back to the main list ("Not a stranger").
    // Overrides BOTH the shield and the captured set, so a trusted stranger stays in the main list even
    // while the shield is on and even though they are not a contact. Same lock-free per-account shape.
    private val allowed = Array(UserConfig.MAX_ACCOUNT_COUNT) { ConcurrentHashMap.newKeySet<Long>() }

    private fun prefs(): SharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun keyFor(account: Int) = KEY_CAPTURED_PREFIX + account
    private fun keyForAllowed(account: Int) = KEY_ALLOWED_PREFIX + account
    private fun keyForEnabled(account: Int) = KEY_ENABLED_PREFIX + account

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs()
            // Anyone who had the old global switch on keeps protection on every account until they say
            // otherwise; a per-account key, once written, always wins over it.
            val legacyEnabled = p.getBoolean(KEY_ENABLED_LEGACY, false)
            for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
                if (p.getBoolean(keyForEnabled(a), legacyEnabled)) enabled.set(a, 1)
                val raw = p.getString(keyFor(a), "") ?: ""
                if (raw.isNotEmpty()) {
                    for (s in raw.split(',')) s.toLongOrNull()?.let { captured[a].add(it) }
                }
                val rawAllowed = p.getString(keyForAllowed(a), "") ?: ""
                if (rawAllowed.isNotEmpty()) {
                    for (s in rawAllowed.split(',')) s.toLongOrNull()?.let { allowed[a].add(it) }
                }
            }
            // One-time migration of the old global set → account 0 (ids are account-scoped now).
            val legacy = p.getString(KEY_CAPTURED_LEGACY, null)
            if (!legacy.isNullOrEmpty()) {
                for (s in legacy.split(',')) s.toLongOrNull()?.let { captured[0].add(it) }
                p.edit().remove(KEY_CAPTURED_LEGACY)
                    .putString(keyFor(0), captured[0].joinToString(",")).apply()
            }
            loaded = true
        }
    }

    /** O(1) cached hot-path read for [account] — a volatile read, never disk. */
    @JvmStatic
    fun isEnabled(account: Int): Boolean {
        ensureLoaded()
        return enabled.get(account) == 1
    }

    @JvmStatic
    fun setEnabled(account: Int, value: Boolean) {
        ensureLoaded()
        if (isEnabled(account) == value) return
        enabled.set(account, if (value) 1 else 0)
        prefs().edit().putBoolean(keyForEnabled(account), value).apply()   // async, no ANR
        if (value) {
            // Re-arming is a FRESH START — one rule, no hidden exceptions: every current stranger gets
            // filed, including ones released with "Not a stranger" last time. They don't drift back on
            // their own; this only runs when the user deliberately switches protection back on, which
            // is exactly the instruction "file all strangers". It is also the undo for a mis-tapped
            // "Not a stranger": toggle off, toggle on. Must run BEFORE the capture pass below, which
            // skips whitelisted ids.
            clearAllowed(account)
            // Turning ON: file every current stranger into the inbox, and clear any
            // notification/app-badge they already accumulated.
            captureCurrentStrangers(account)
            NotificationsController.getInstance(account).fenixDismissStrangers()
        }
        // Drop ids that are no longer strangers / no longer exist — keeps both sets bounded.
        pruneCaptured(account)
        pruneAllowed(account)
        // Recompute the bottom "Chats" tab badge (drops strangers on, restores non-captured off).
        MessagesStorage.getInstance(account).fenixRecalcMainUnread()
    }

    /**
     * A real non-bot user who is NOT in your contacts and NOT yourself (Saved Messages never hidden).
     * Telegram's own official accounts are NEVER strangers: the service accounts (777000 login codes /
     * 333000 / 42777) and Telegram support staff (`user.support`) must stay in the main chat list.
     */
    @JvmStatic
    fun isStranger(user: TLRPC.User?): Boolean =
        user != null && !user.contact && !user.bot && !user.support &&
            !UserObject.isUserSelf(user) && !UserObject.isService(user.id)

    /**
     * THE single rule: does this dialog belong in the stranger inbox (hidden from the main list,
     * silenced, badge-excluded) for [account]? A stranger AND (the shield is on now, OR it was captured
     * while on in this account). Pure — no side effects; capture is done explicitly via [capture].
     */
    @JvmStatic
    fun belongsInInbox(account: Int, user: TLRPC.User?, dialogId: Long): Boolean {
        if (!isStranger(user)) return false
        if (allowed[account].contains(dialogId)) return false   // explicitly trusted → always in main list
        return isEnabled(account) || captured[account].contains(dialogId)
    }

    /**
     * File a stranger into [account]'s inbox (called while the shield is on). Idempotent, async-saved.
     * A trusted (whitelisted) id is never re-filed — otherwise the render hot-path would keep re-capturing
     * a stranger the user just moved out.
     */
    @JvmStatic
    fun capture(account: Int, dialogId: Long) {
        ensureLoaded()
        if (allowed[account].contains(dialogId)) return
        if (captured[account].add(dialogId)) saveCaptured(account)
    }

    @JvmStatic
    fun hasCaptured(account: Int): Boolean {
        ensureLoaded()
        return captured[account].isNotEmpty()
    }

    private fun saveCaptured(account: Int) {
        prefs().edit().putString(keyFor(account), captured[account].joinToString(",")).apply()
    }

    /**
     * Move a stranger OUT of the inbox back to the main list ("Not a stranger"): whitelist the id (so it
     * stays out even while the shield is on) and drop it from the captured set. Device-only, async-saved.
     * The caller refreshes the dialog list (dialogsNeedReload) and the main badge after moving one or more.
     */
    @JvmStatic
    fun trust(account: Int, dialogId: Long) {
        ensureLoaded()
        if (allowed[account].add(dialogId)) saveAllowed(account)
        if (captured[account].remove(dialogId)) saveCaptured(account)
    }

    private fun saveAllowed(account: Int) {
        prefs().edit().putString(keyForAllowed(account), allowed[account].joinToString(",")).apply()
    }

    /**
     * How many chats [account]'s inbox is currently holding on to purely because they were captured —
     * i.e. how many would come back to the main list if the shield were switched off and released. Uses
     * the same keep-rule as [pruneCaptured], so the number the user is shown matches what actually moves.
     */
    @JvmStatic
    fun countCaptured(account: Int): Int {
        ensureLoaded()
        val set = captured[account]
        if (set.isEmpty()) return 0
        return try {
            val mc = MessagesController.getInstance(account)
            var c = 0
            for (id in set) {
                val user = mc.getUser(id)
                // Not loaded yet (cold start) but the dialog exists → still counts, same as [pruneCaptured].
                if (if (user != null) isStranger(user) else mc.dialogs_dict.get(id) != null) c++
            }
            c
        } catch (e: Exception) {
            FileLog.e(e)
            set.size
        }
    }

    /**
     * "Return them to the main list": empty [account]'s inbox. Only the captured set is dropped — the
     * per-chat "Not a stranger" whitelist is untouched, and re-arming the shield later files these chats
     * again (that is the difference between this and [trust]). Caller reloads the dialog list.
     */
    @JvmStatic
    fun releaseCaptured(account: Int) {
        ensureLoaded()
        if (captured[account].isEmpty()) return
        captured[account].clear()
        saveCaptured(account)
        // The bottom "Chats" badge subtracted these while they were inboxed — put them back.
        MessagesStorage.getInstance(account).fenixRecalcMainUnread()
    }

    /** Forget every "Not a stranger" whitelist entry for [account] (called when protection is re-armed). */
    private fun clearAllowed(account: Int) {
        if (allowed[account].isEmpty()) return
        allowed[account].clear()
        saveAllowed(account)
    }

    /** Same bounding as [pruneCaptured]: drop whitelisted ids that became contacts or whose dialog is gone. */
    private fun pruneAllowed(account: Int) {
        val set = allowed[account]
        if (set.isEmpty()) return
        try {
            val mc = MessagesController.getInstance(account)
            var changed = false
            val it = set.iterator()
            while (it.hasNext()) {
                val id = it.next()
                val user = mc.getUser(id)
                if (user != null) {
                    if (!isStranger(user)) {
                        it.remove(); changed = true
                    }
                } else if (mc.dialogs_dict.get(id) == null) {
                    it.remove(); changed = true
                }
            }
            if (changed) saveAllowed(account)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    /** Snapshot every current stranger dialog into [account]'s set (called when the shield turns on). */
    private fun captureCurrentStrangers(account: Int) {
        try {
            val mc = MessagesController.getInstance(account)
            val set = captured[account]
            var changed = false
            for (d in ArrayList(mc.allDialogs)) {
                if (d != null && DialogObject.isUserDialog(d.id) && isStranger(mc.getUser(d.id))
                        && !allowed[account].contains(d.id)) {
                    if (set.add(d.id)) changed = true
                }
            }
            if (changed) saveCaptured(account)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    /**
     * Remove captured ids that are no longer worth tracking, so the set can't grow without bound:
     *  - the user became a contact / bot / service (no longer a stranger), or
     *  - the dialog no longer exists (deleted) and the user isn't loaded.
     * Conservative: an id whose user just isn't loaded yet but whose dialog still exists is KEPT, so a
     * cold start never drops a valid stranger.
     */
    private fun pruneCaptured(account: Int) {
        val set = captured[account]
        if (set.isEmpty()) return
        try {
            val mc = MessagesController.getInstance(account)
            var changed = false
            val it = set.iterator()
            while (it.hasNext()) {
                val id = it.next()
                val user = mc.getUser(id)
                if (user != null) {
                    if (!isStranger(user)) {
                        it.remove(); changed = true
                    }
                } else if (mc.dialogs_dict.get(id) == null) {
                    it.remove(); changed = true
                }
            }
            if (changed) saveCaptured(account)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    /**
     * Total unread stranger chats currently filed in [account]'s inbox, in ANY shield state — used to
     * badge the "Stranger chats" entry so the user is reminded and never misses a message. Counts 1 per
     * chat that has unread (or an unread mark), matching [belongsInInbox] for what's actually inboxed.
     */
    @JvmStatic
    fun countInboxUnread(account: Int): Int {
        ensureLoaded()
        return try {
            val mc = MessagesController.getInstance(account)
            var c = 0
            for (d in ArrayList(mc.allDialogs)) {
                if (d == null || !DialogObject.isUserDialog(d.id)) continue
                if (!belongsInInbox(account, mc.getUser(d.id), d.id)) continue
                if (d.unread_count > 0 || d.unread_mark) c++
            }
            c
        } catch (e: Exception) {
            FileLog.e(e)
            0
        }
    }

    /**
     * Number of captured (inboxed) stranger chats in [account] whose unread the bottom "Chats" tab
     * badge is still counting — used to subtract them when the shield is OFF (while ON,
     * calcUnreadCounters already excludes all strangers, so this returns 0). Matches the badge's "1 per
     * unread chat, muted only if badgeNumberMuted" semantics so it never over-subtracts.
     */
    @JvmStatic
    fun countInboxedUnreadChats(account: Int): Int {
        ensureLoaded()
        val set = captured[account]
        if (isEnabled(account) || set.isEmpty()) return 0
        return try {
            val mc = MessagesController.getInstance(account)
            val countMuted = NotificationsController.getInstance(account).showBadgeMuted
            var c = 0
            for (id in set) {
                val d = mc.dialogs_dict.get(id) ?: continue
                if (!isStranger(mc.getUser(id))) continue
                if (d.unread_count <= 0 && !d.unread_mark) continue
                if (!countMuted && mc.isDialogMuted(id, 0)) continue
                c++
            }
            c
        } catch (e: Exception) {
            FileLog.e(e)
            0
        }
    }
}
