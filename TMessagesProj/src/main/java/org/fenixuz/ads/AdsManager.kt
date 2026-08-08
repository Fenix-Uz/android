package org.fenixuz.ads

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

/**
 * Orchestrates the sponsored-channel search ad: debounced lookup, stale-response guarding, channel
 * resolve, and click reporting. Everything here runs on the UI thread (the search adapter calls in on
 * the UI thread; the retrofit callbacks and the resolveUsername callback hop back via runOnUIThread),
 * so the mutable state below needs no locking.
 *
 * No long-lived scope, no Context/View retained: the only state is the last shown ad (a tiny DTO) plus
 * a monotonic [generation] used to drop results from a superseded query. Every new query cancels the
 * previous debounce + in-flight call, so callbacks are bounded by a single request's lifetime.
 */
object AdsManager {

    private const val MIN_QUERY_LEN = 4
    private const val DEBOUNCE_MS = 500L

    /** The ad currently shown in search, kept so a tap can be attributed to its order. */
    private var currentResult: AdSearchResult? = null

    /**
     * The REAL Telegram channel id we actually resolved and displayed (positive form). We attribute clicks
     * to this — not to the advertiser-typed [AdSearchResult.channelId], which may be wrong/stale and is only
     * used as a hint. 0 when no ad is shown.
     */
    private var currentChannelId: Long = 0L

    /** Bumped on every new query / cancel; a callback whose captured value differs is stale and ignored. */
    private var generation = 0

    private var pendingCall: Call<AdSearchResult>? = null
    private var debounceRunnable: Runnable? = null

    /**
     * Requests the ad for [query]. [onResult] is invoked on the UI thread with the sponsored channel id
     * (positive chat id) and the ad's platform ("TELEGRAM"/"NOVAGRAM", null when unknown) when there is one
     * to show, or (null, null) (no ad / too-short query / error). It may be called synchronously (short
     * query) or after the debounce + network round-trip.
     */
    fun requestAd(query: String?, onResult: (Long?, String?) -> Unit) {
        cancel()
        // No backend URL configured (ADS_BASE_URL absent from local.properties) → ads disabled, never call out.
        if (!AdsRetrofitClient.enabled || query == null || query.length < MIN_QUERY_LEN) {
            onResult(null, null)
            return
        }
        val gen = generation
        val runnable = Runnable {
            // We're executing now: release our reference to this runnable (and, through it, the caller's
            // callback) so the static manager never retains a stale adapter after the request is kicked off.
            debounceRunnable = null
            val account = UserConfig.selectedAccount
            val viewerId = UserConfig.getInstance(account).clientUserId
            if (viewerId == 0L) {
                onResult(null, null)
                return@Runnable
            }
            val call = AdsRetrofitClient.service.searchAd(AdSearchRequest(query, viewerId.toString()))
            pendingCall = call
            call.enqueue(object : Callback<AdSearchResult> {
                override fun onResponse(call: Call<AdSearchResult>, response: Response<AdSearchResult>) {
                    if (gen != generation) return
                    pendingCall = null
                    // 404 = "no ad for this query" (not an error); body is null → show nothing.
                    val body = if (response.isSuccessful) response.body() else null
                    // channel_id is an advertiser-typed hint (may be wrong). channel_name is the @username we
                    // actually resolve the channel by, since a public channel can only be opened via username.
                    val rawId = body?.channelId?.toLongOrNull()
                    val hintId = if (rawId != null) normalizeChannelId(rawId) else 0L
                    if (body == null) {
                        currentResult = null
                        currentChannelId = 0L
                        onResult(null, null)
                        return
                    }
                    resolveChannel(account, hintId, body.channelName) { resolvedId ->
                        if (gen != generation) return@resolveChannel
                        if (resolvedId != 0L) {
                            currentResult = body
                            currentChannelId = resolvedId
                            onResult(resolvedId, body.platform)
                        } else {
                            currentResult = null
                            currentChannelId = 0L
                            onResult(null, null)
                        }
                    }
                }

                override fun onFailure(call: Call<AdSearchResult>, t: Throwable) {
                    if (gen != generation) return
                    pendingCall = null
                    currentResult = null
                    currentChannelId = 0L
                    onResult(null, null)
                }
            })
        }
        debounceRunnable = runnable
        AndroidUtilities.runOnUIThread(runnable, DEBOUNCE_MS)
    }

    /** Drops any pending lookup and invalidates in-flight callbacks. UI thread. */
    fun cancel() {
        generation++
        debounceRunnable?.let { AndroidUtilities.cancelRunOnUIThread(it) }
        debounceRunnable = null
        pendingCall?.cancel()
        pendingCall = null
    }

    /** Reports a click when the user opens the currently shown sponsored channel. UI thread. */
    /**
     * The API returns channel_id as a string that may be the positive channel id, the old -id dialog
     * form, or the -100… bot-API form (e.g. "-1001699035352"). MessagesController.getChat() wants the
     * positive channel id, so normalise all three to that.
     */
    private fun normalizeChannelId(raw: Long): Long {
        if (raw >= 0) return raw
        val abs = -raw
        return if (abs in 1_000_000_000_000L..1_999_999_999_999L) abs - 1_000_000_000_000L else abs
    }

    fun clickAd(dialogId: Long) {
        val result = currentResult ?: return
        // Attribute to the channel we actually resolved and showed, not the advertiser-typed channel_id.
        val channelId = currentChannelId
        if (channelId == 0L) {
            return
        }
        if (dialogId != channelId && -dialogId != channelId) {
            return
        }
        val orderId = result.orderId ?: return
        val account = UserConfig.selectedAccount
        val viewerId = UserConfig.getInstance(account).clientUserId
        if (viewerId == 0L) {
            return
        }
        AdsRetrofitClient.service.clickAd(AdClickRequest(orderId, viewerId.toString()))
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {}
                override fun onFailure(call: Call<Void>, t: Throwable) {}
            })
    }

    /**
     * Resolves the ad channel and returns its REAL positive channel id via [cb] (0 = could not resolve).
     *
     * A public channel can only be opened by its @username, so [username] (the ad's channel_name) is the
     * source of truth here; [hintId] is the advertiser-typed channel_id used only as a fast-path when that
     * channel already happens to be in the model. We deliberately do NOT require the resolved channel's id
     * to equal [hintId] — advertisers type that id by hand and it is frequently wrong, which used to drop
     * the ad even after a perfectly good username resolve.
     */
    private fun resolveChannel(account: Int, hintId: Long, username: String?, cb: (Long) -> Unit) {
        val mc = MessagesController.getInstance(account)
        if (hintId != 0L && mc.getChat(hintId) != null) {
            cb(hintId)
            return
        }
        if (username.isNullOrEmpty()) {
            cb(0L)
            return
        }
        val req = TLRPC.TL_contacts_resolveUsername()
        req.username = username.lowercase(Locale.getDefault()).trim()
        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            AndroidUtilities.runOnUIThread {
                if (error != null || response !is TLRPC.TL_contacts_resolvedPeer) {
                    cb(0L)
                    return@runOnUIThread
                }
                mc.putUsers(response.users, false)
                mc.putChats(response.chats, false)
                // Prefer the channel the server pointed the username at; fall back to the first channel in
                // the response. Only channels can be shown as an ad row (a user/bot username → no ad).
                var resolvedId = 0L
                val peer = response.peer
                if (peer != null && peer.channel_id != 0L) {
                    resolvedId = peer.channel_id
                } else if (response.chats.isNotEmpty()) {
                    resolvedId = response.chats[0].id
                }
                cb(if (resolvedId != 0L && mc.getChat(resolvedId) != null) resolvedId else 0L)
            }
        }
    }
}
