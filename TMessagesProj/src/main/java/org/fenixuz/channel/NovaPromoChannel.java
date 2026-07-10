package org.fenixuz.channel;

import android.content.SharedPreferences;
import android.util.SparseArray;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Novagram: shows our own official channel as the FIRST row of the main chat list, but ONLY while
 * the user has not joined it. Which channel depends on the account's phone country: Uzbekistan
 * (+998) gets the Uzbek channel, everyone else (Russia + the rest, for now) gets the default one.
 *
 * <p>The channel is injected as a synthetic {@link TLRPC.TL_dialog} into the real model
 * (dialogs_dict + allDialogs) so the stock {@code DialogCell} renders it correctly — that cell reads
 * its data back from dialogs_dict by id, which is exactly why a display-only row showed the wrong
 * name. Membership is read from the model (a real dialog at that id) plus the channel's {@code left}
 * flag, so the row disappears on its own once the user joins. To avoid a duplicate when the user
 * subscribes, the synthetic row is removed the moment it is tapped (before the join), and re-added
 * on the next reconcile if the user came back without joining.
 *
 * <p>Channel usernames + a kill switch come from Firebase Remote Config (so the RU channel can be
 * swapped without an app update); the in-code defaults keep it working before/without any fetch.
 * The resolve RPC runs at most once per account per session (throttled) and is skipped for members.
 *
 * <p>All model mutation happens on the UI thread (reconcile is called from DialogsActivity; the
 * resolve callback hops back via runOnUIThread), matching how MessagesController expects its dialog
 * structures to be touched.
 */
public final class NovaPromoChannel {

    private static final String RC_ENABLED = "promo_channel_enabled";
    private static final String RC_UZ = "promo_channel_uz";
    private static final String RC_DEFAULT = "promo_channel_default";

    private static final String DEF_UZ = "Novagramtg";
    private static final String DEF_RU = "Novagram_Ru";

    private static final String PREF = "nova_promo_channel";
    private static final long RESOLVE_THROTTLE_MS = 30_000L;

    private static final class State {
        String username;
        long channelId;         // positive channel id; the dialog id is -channelId
        TLRPC.Dialog dialog;    // our injected synthetic row, or null
        boolean injected;
        boolean resolving;
        long lastResolveAttempt;
    }

    private static final SparseArray<State> STATES = new SparseArray<>();

    private static volatile boolean rcLoaded;
    private static volatile boolean rcLoading;
    private static volatile boolean rcEnabled = true;
    private static volatile String rcUz = DEF_UZ;
    private static volatile String rcDefault = DEF_RU;

    private NovaPromoChannel() {
    }

    /** True if {@code dialogId} is our injected promo row (used to guard long-press and to intercept taps). */
    public static boolean isPromoDialog(int account, long dialogId) {
        State s = STATES.get(account);
        return s != null && s.injected && s.dialog != null && s.dialog.id == dialogId;
    }

    /**
     * Called from {@link MessagesController#deleteDialog} when a dialog is removed. If it is our promo
     * channel, the user just left it — but a plain channel leave does NOT flip the in-memory
     * {@code chat.left} flag (only Telegram's own promo does), so {@code evaluate()} would keep treating
     * the user as a member and never re-show the row until a cold restart reloads the chat as left.
     * We mirror what Telegram does for its promo: mark the chat left, then reconcile so the row comes back.
     *
     * <p>The reconcile is deferred to the next UI-loop tick because at the dialogDeleted call site the
     * real dialog has not been pulled out of the model yet; running after the deletion completes lets
     * {@code evaluate()} see the absent dialog and re-inject. UI thread.
     */
    public static void onDialogDeleted(int account, long dialogId) {
        State s = STATES.get(account);
        if (s == null || s.channelId == 0 || dialogId != -s.channelId) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            State st = STATES.get(account);
            if (st == null || st.channelId == 0 || -st.channelId != dialogId) {
                return;
            }
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(st.channelId);
            if (chat != null) {
                chat.left = true;
            }
            reconcile(account);
        });
    }

    /** Sets up / refreshes the promo for an account. Safe to call often (throttled). UI thread. */
    public static void reconcile(int account) {
        if (rcLoaded) {
            reconcileInternal(account);
        } else {
            loadRcAsync(() -> reconcileInternal(account));
        }
    }

    private static void reconcileInternal(int account) {
        State s = STATES.get(account);
        if (s == null) {
            s = new State();
            STATES.put(account, s);
        }
        if (!rcEnabled) {
            removeInjected(account, s, false);
            return;
        }
        UserConfig uc = UserConfig.getInstance(account);
        if (!uc.isClientActivated()) {
            return;
        }
        String phone = uc.getClientPhone();
        if (phone == null) {
            phone = "";
        }
        if (phone.startsWith("+")) {
            phone = phone.substring(1);
        }
        String username = phone.startsWith("998") ? rcUz : rcDefault;
        if (username == null || username.isEmpty()) {
            return;
        }
        // Account switch or an RC username change → drop the previous channel first (and re-arm).
        if (!username.equals(s.username)) {
            removeInjected(account, s, false);
            s.username = username;
            s.channelId = 0;
        }

        if (s.channelId == 0) {
            s.channelId = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREF, 0).getLong("id_" + username, 0);
        }
        if (s.channelId == 0) {
            resolve(account, s, username);
            return;
        }
        evaluate(account, s);
    }

    /** Decides inject vs remove for a known channel id. UI thread. */
    private static void evaluate(int account, State s) {
        MessagesController mc = MessagesController.getInstance(account);
        long did = -s.channelId;

        TLRPC.Dialog cur = mc.dialogs_dict.get(did);
        boolean realDialog = cur != null && cur != s.dialog;
        TLRPC.Chat chat = mc.getChat(s.channelId);
        boolean member = realDialog || (chat != null && !chat.left);

        if (member) {
            removeInjected(account, s, true);
            return;
        }
        if (chat == null) {
            // We need the channel object to render the row; fetch it once.
            resolve(account, s, s.username);
            return;
        }
        if (!s.injected) {
            mc.putChat(chat, true);
            s.dialog = buildDialog(did);
            if (mc.novaAddPromoDialog(s.dialog)) {
                s.injected = true;
            } else {
                s.dialog = null;
            }
        }
    }

    private static void removeInjected(int account, State s, boolean member) {
        if (s.injected && s.dialog != null) {
            // When the user just joined, ask MessagesController to fetch the real dialog if it hasn't
            // synced yet, so the channel doesn't vanish when our synthetic row is dropped.
            MessagesController.getInstance(account).novaRemovePromoDialog(s.dialog, member);
        }
        s.injected = false;
        s.dialog = null;
    }

    private static void resolve(int account, final State s, final String username) {
        if (s.resolving) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - s.lastResolveAttempt < RESOLVE_THROTTLE_MS) {
            return;
        }
        s.resolving = true;
        s.lastResolveAttempt = now;

        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            s.resolving = false;
            if (!(res instanceof TLRPC.TL_contacts_resolvedPeer)) {
                return;
            }
            TLRPC.TL_contacts_resolvedPeer r = (TLRPC.TL_contacts_resolvedPeer) res;
            if (r.chats == null || r.chats.isEmpty()) {
                return;
            }
            MessagesController mc = MessagesController.getInstance(account);
            mc.putUsers(r.users, false);
            mc.putChats(r.chats, false);

            TLRPC.Chat chat = r.chats.get(0);
            s.channelId = chat.id;
            ApplicationLoader.applicationContext.getSharedPreferences(PREF, 0)
                    .edit().putLong("id_" + username, chat.id).apply();
            // Only act if this is still the account's active channel (username unchanged).
            if (username.equals(s.username)) {
                evaluate(account, s);
            }
        }));
    }

    private static TLRPC.Dialog buildDialog(long did) {
        TLRPC.Dialog d = new TLRPC.TL_dialog();
        d.id = did;
        d.folder_id = 0;
        d.pinned = true;               // float to the very top of the list
        d.pinnedNum = 1_000_000_000;   // far above any real pin, but +1 still can't overflow
        d.unread_count = 0;
        d.last_message_date = 0;
        return d;
    }

    private static void loadRcAsync(final Runnable then) {
        if (rcLoading) {
            return;
        }
        rcLoading = true;
        Thread t = new Thread(() -> {
            try {
                FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();
                HashMap<String, Object> defaults = new HashMap<>();
                defaults.put(RC_ENABLED, true);
                defaults.put(RC_UZ, DEF_UZ);
                defaults.put(RC_DEFAULT, DEF_RU);
                try {
                    Tasks.await(rc.setDefaultsAsync(defaults), 5, TimeUnit.SECONDS);
                } catch (Throwable ignore) {
                }
                try {
                    Tasks.await(rc.fetchAndActivate(), 8, TimeUnit.SECONDS);
                } catch (Throwable ignore) {
                }
                rcEnabled = rc.getBoolean(RC_ENABLED);
                String u = rc.getString(RC_UZ);
                if (u != null && !u.isEmpty()) {
                    rcUz = u;
                }
                String d = rc.getString(RC_DEFAULT);
                if (d != null && !d.isEmpty()) {
                    rcDefault = d;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                rcLoaded = true;
                rcLoading = false;
                if (then != null) {
                    AndroidUtilities.runOnUIThread(then);
                }
            }
        }, "nova-promo-channel-rc");
        t.setDaemon(true);
        t.start();
    }
}
