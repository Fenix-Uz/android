package org.fenixuz.proxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Novagram: region-gated auto-proxy for censored regions (currently Russia).
 *
 * Why: where Telegram's data centers are DPI-blocked, a fresh user can't even reach the login
 * servers. So on startup — BEFORE login — if the device looks Russian we pull a SOCKS5 proxy list
 * from Firebase Remote Config, push it into Telegram's own proxy layer ({@link SharedConfig}) and
 * turn proxying + rotation on. The rotation controller then fails over automatically when a proxy
 * dies, which datacenter proxies do under censorship.
 *
 * The proxy credentials NEVER live in the app or the (public) repo — they sit only in the Remote
 * Config console, so blocked entries can be swapped without shipping an update.
 *
 * Rules that keep this from fighting the user / hurting startup:
 *  - does NOTHING unless the owner grants the remote master permit ({@link #RC_ACTIVE}); a
 *    server-delivered {@code false} is also a kill switch that shuts our proxy down on every device at
 *    once (used when the pool goes bad) — an absent field or a failed fetch never kills;
 *  - never touches anything if the user already enabled a proxy themselves;
 *  - can be disabled by the user via {@link #PREF_AUTO};
 *  - once we have auto-applied a proxy and the user then turns it OFF (the switch, deleting the active
 *    proxy, or dismissing Telegram's "proxy error" dialog), we never re-enable it on later launches
 *    ({@link #PREF_APPLIED} gate) — otherwise a dead proxy pool would trap the user in a loop where every
 *    restart puts them back on a proxy stuck at "Connecting…";
 *  - the Remote Config fetch is bounded ({@link #FETCH_TIMEOUT_SECONDS}) so a hanging fetch never
 *    blocks the feature — on timeout we fall back to the last-activated / default values;
 *  - the whole pool is saved to disk ONCE (not per entry), so a 1000-proxy list doesn't jank;
 *  - network + JSON run off the main thread; only the final list mutation runs on it.
 */
public final class RegionProxyManager {

    /** Remote Config key: a JSON array of {"ip","port","user","pass"} SOCKS5 entries. */
    private static final String RC_KEY = "ru_proxy_list";
    /** Remote Config key: comma-separated ISO country codes to auto-proxy (default "ru"). */
    private static final String RC_REGIONS = "proxy_regions";
    /**
     * Remote Config MASTER permit (default false): auto-proxy does nothing unless the owner sets this to
     * true in the console. A server-delivered {@code false} is also the KILL switch — it tears down a proxy
     * we applied so a bad pool can be stopped on every device at once. Absent field / failed fetch never
     * kills (only an explicit REMOTE {@code false} does).
     */
    private static final String RC_ACTIVE = "ru_proxy_active";
    /** User switch for Novagram's auto-proxy (default on). */
    public static final String PREF_AUTO = "novagram_region_proxy_auto";
    /** Marks that we, not the user, turned the current proxy on. */
    public static final String PREF_APPLIED = "novagram_region_proxy_applied";
    /** "ip:port" of the proxy we last auto-applied, so the kill switch only ever disables OUR proxy. */
    private static final String PREF_APPLIED_ENDPOINT = "novagram_region_proxy_endpoint";

    private static final long FETCH_TIMEOUT_SECONDS = 15;

    /** How long the auto-proxy gets to actually connect before we treat the whole pool as dead. */
    private static final long WATCHDOG_MS = 30_000;
    /** After a dead-pool disable, how long the user runs direct before auto-proxy may re-attach. */
    private static final long BACKOFF_MS = 60 * 60 * 1000L;
    /** Epoch-ms until which auto-proxy stays off after the watchdog found the pool dead. */
    private static final String PREF_BACKOFF_UNTIL = "novagram_region_proxy_backoff_until";

    private static volatile boolean running;

    private RegionProxyManager() {
    }

    /**
     * Entry point — call once, early in startup. Cheap and safe to call more than once; it no-ops
     * unless the user hasn't set up their own proxy and we're not already working. The real
     * region decision is made after the Remote Config fetch.
     */
    public static void maybeAutoConnect() {
        if (running) {
            return;
        }
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        if (!prefs.getBoolean(PREF_AUTO, true)) {
            return; // user opted out of Novagram auto-proxy
        }
        boolean proxyOn = prefs.getBoolean("proxy_enabled", false);
        boolean weApplied = prefs.getBoolean(PREF_APPLIED, false);
        // The active proxy is the user's OWN (enabled, but we never applied it) — never touch it.
        if (proxyOn && !weApplied) {
            return;
        }
        // Our auto-proxy was applied on an earlier run and is now OFF. The proxy only turns off by a
        // deliberate act — the user flipping the switch, deleting the active proxy, or dismissing Telegram's
        // "proxy error" dialog (nothing auto-writes proxy_enabled=false; ProxyRotationController only ever
        // sets it true). Re-enabling here would fight the user: a dead proxy pool means proxy fails → they
        // turn it off → next launch we switch it back on → stuck at "Connecting…". So once our proxy has
        // been turned off by the user, we leave it off; they re-enable a proxy by hand if they want one.
        if (!proxyOn && weApplied) {
            return;
        }
        // What remains needs the network decision, made after the Remote Config fetch:
        //  - proxy OFF & we never applied → fresh candidate: attach only if the owner granted the remote
        //    master permit (ru_proxy_active) and the region matches;
        //  - proxy ON  & we applied it    → our proxy is up: we still fetch so the remote KILL switch can
        //    reach it and shut it down everywhere at once if our pool goes bad.
        // Fresh candidate only (here !proxyOn ⟺ off & never-applied): honor the watchdog cooldown. If a
        // recent launch found the whole pool dead and disabled our proxy, don't re-attach it every restart —
        // let the user run direct for a while (the pool may be swapped in the console meanwhile).
        if (!proxyOn && System.currentTimeMillis() < prefs.getLong(PREF_BACKOFF_UNTIL, 0)) {
            return;
        }
        running = true;
        // Dedicated daemon thread: fetchAndDecide() blocks (bounded) on the Remote Config fetch, so
        // keep it off both the main thread and the shared dispatch queues.
        Thread t = new Thread(RegionProxyManager::fetchAndDecide, "novagram-region-proxy");
        t.setDaemon(true);
        t.start();
    }

    private static void fetchAndDecide() {
        try {
            FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();

            // Safe in-app defaults so the gate is deterministic even before the first server fetch.
            HashMap<String, Object> defaults = new HashMap<>();
            defaults.put(RC_ACTIVE, false);
            defaults.put(RC_REGIONS, "ru");
            defaults.put(RC_KEY, "");
            try {
                Tasks.await(rc.setDefaultsAsync(defaults), 5, TimeUnit.SECONDS);
            } catch (Throwable ignore) {
            }

            // Under censorship the live pool changes often, so don't sit on a 12h cache — let a
            // blocked-entry swap in the console reach clients within the hour.
            rc.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build());

            // Bounded wait: a hanging fetch must NEVER block auto-proxy forever — critical on the
            // throttled networks this feature targets. On timeout we fall back to last-activated /
            // default values instead of waiting indefinitely.
            try {
                Tasks.await(rc.fetchAndActivate(), FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Throwable ignore) {
            }

            // Master remote permit. Auto-proxy does NOTHING unless the owner explicitly grants it by setting
            // ru_proxy_active=true in the console — the "only works if I allow it" gate.
            FirebaseRemoteConfigValue activeValue = rc.getValue(RC_ACTIVE);
            boolean active = activeValue.asBoolean();
            boolean fromServer = activeValue.getSource() == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE;
            if (!active) {
                // Permit not granted. If the server EXPLICITLY delivered active=false, that is the kill
                // switch — shut our proxy down. But a missing field, a failed fetch or the in-app default
                // must NEVER mass-kill a working proxy, so we only kill on a real REMOTE value.
                if (fromServer) {
                    FileLog.d("RegionProxyManager: remote permit OFF — kill switch");
                    AndroidUtilities.runOnUIThread(RegionProxyManager::killOnMain);
                    return;
                }
                running = false;
                return;
            }

            ArrayList<String> countries = deviceCountries();
            boolean allowed = regionAllowed(rc.getString(RC_REGIONS), countries);
            FileLog.d("RegionProxyManager: countries=" + countries + " allowed=" + allowed);
            if (!allowed) {
                // Not physically in an allowed region (Russia). If a proxy WE auto-applied is still on this
                // device — e.g. left over from the earlier locale-based check that could misfire on a
                // Russian-language phone — tear it down so a non-Russian user is never stranded on our proxy.
                // killOnMain only ever disables our own applied endpoint, never a proxy the user set up.
                AndroidUtilities.runOnUIThread(RegionProxyManager::killOnMain);
                return;
            }
            ArrayList<SharedConfig.ProxyInfo> parsed = parseList(rc.getString(RC_KEY));
            if (parsed.isEmpty()) {
                running = false;
                return;
            }
            AndroidUtilities.runOnUIThread(() -> applyOnMain(parsed));
        } catch (Throwable e) {
            running = false;
        }
    }

    /**
     * The ONE signal that reflects where the device physically IS right now: the registered-network country
     * (`getNetworkCountryIso`) — the country of the cell tower the phone is currently camped on, which flips
     * to the VISITED country while roaming. Lower-cased; empty dropped. Login-free, no permission, offline.
     *
     * We deliberately use NOTHING else:
     *  - NOT the SIM country — a SIM tells you where the number was issued, not where the person is. Someone
     *    who travels INTO Russia keeps their foreign SIM (so SIM≠ru though they're in Russia and need the
     *    proxy), while a Russian who travels OUT keeps a Russian SIM (SIM=ru though they're abroad and don't).
     *    Only the serving network says "physically here now".
     *  - NOT the locale/region setting — that is a language preference (an Uzbek phone running in Russian
     *    reports region "RU"), the exact false positive that once wrongly proxied a non-Russian device.
     *
     * Cost of this precision: a Wi-Fi-only device with no cellular registration reports no network country, so
     * we can't confirm Russia and don't attach. That's the safe trade — better to miss than to mis-proxy.
     */
    private static ArrayList<String> deviceCountries() {
        ArrayList<String> out = new ArrayList<>();
        try {
            Context ctx = ApplicationLoader.applicationContext;
            TelephonyManager tm = ctx == null ? null : (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                addCountry(out, tm.getNetworkCountryIso());
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    private static void addCountry(ArrayList<String> out, String raw) {
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        String c = raw.toLowerCase(Locale.ROOT);
        if (!out.contains(c)) {
            out.add(c);
        }
    }

    /** True if ANY of {@code countries} is listed in the comma-separated {@code regionsCsv} (default "ru"). */
    private static boolean regionAllowed(String regionsCsv, ArrayList<String> countries) {
        if (countries.isEmpty()) {
            return false;
        }
        String csv = TextUtils.isEmpty(regionsCsv) ? "ru" : regionsCsv;
        HashSet<String> allowed = new HashSet<>();
        for (String r : csv.split(",")) {
            String t = r.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                allowed.add(t);
            }
        }
        for (String c : countries) {
            if (allowed.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** Parse the Remote Config JSON into ProxyInfo entries. Pure — touches no shared state. */
    private static ArrayList<SharedConfig.ProxyInfo> parseList(String json) {
        ArrayList<SharedConfig.ProxyInfo> out = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String ip = o.optString("ip");
                int port = o.optInt("port");
                if (TextUtils.isEmpty(ip) || port <= 0) {
                    continue;
                }
                // SOCKS5 → no MTProto secret.
                out.add(new SharedConfig.ProxyInfo(ip, port, o.optString("user"), o.optString("pass"), ""));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return out;
    }

    /**
     * Merge the parsed proxies into Telegram's proxy list and enable one. Runs on the main thread
     * (where the proxy list is otherwise mutated) and saves the list ONCE — no per-item disk write,
     * so even a 1000-entry pool doesn't jank startup.
     */
    private static void applyOnMain(ArrayList<SharedConfig.ProxyInfo> parsed) {
        try {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            if (prefs.getBoolean("proxy_enabled", false)) {
                // A proxy is already on — don't override it. But if it is OUR applied proxy from a build
                // that predates the endpoint marker (i.e. an upgrade), backfill the marker now so the remote
                // kill switch can target it later. Backfill ONLY when the active proxy is genuinely one from
                // our current pool, so a user's own proxy is never mislabelled as ours.
                if (prefs.getBoolean(PREF_APPLIED, false)
                        && TextUtils.isEmpty(prefs.getString(PREF_APPLIED_ENDPOINT, ""))) {
                    String cur = prefs.getString("proxy_ip", "") + ":" + prefs.getInt("proxy_port", 0);
                    for (SharedConfig.ProxyInfo p : parsed) {
                        if ((p.address + ":" + p.port).equals(cur)) {
                            prefs.edit().putString(PREF_APPLIED_ENDPOINT, cur).apply();
                            break;
                        }
                    }
                }
                // Our proxy is already on from a previous launch (reaching here means it's ours — a user's own
                // proxy was filtered out earlier). Re-arm the watchdog so a pool that died between launches
                // still can't trap the user at "Connecting…" — the case where they'd force-closed before the
                // first watchdog could fire.
                if (prefs.getBoolean(PREF_APPLIED, false)) {
                    AndroidUtilities.runOnUIThread(RegionProxyManager::watchdogCheck, WATCHDOG_MS);
                }
                return;
            }
            SharedConfig.loadProxyList();

            HashSet<String> existing = new HashSet<>();
            for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
                existing.add(key(p));
            }
            boolean changed = false;
            for (SharedConfig.ProxyInfo p : parsed) {
                if (existing.add(key(p))) {
                    SharedConfig.proxyList.add(p);
                    changed = true;
                }
            }
            if (changed) {
                SharedConfig.saveProxyList(); // single write for the whole pool
            }

            // currentProxy must be the SAME instance that lives in proxyList — the rotation checker
            // updates availability/ping on the list instances, so a detached copy would never be tracked.
            // On a fresh install pick was just added above so it already is; this loop also covers the
            // rare case where the same endpoint was already in the list (then pick was not re-added).
            SharedConfig.ProxyInfo pick = parsed.get(0);
            String pickKey = key(pick);
            for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
                if (pickKey.equals(key(p))) {
                    pick = p;
                    break;
                }
            }
            SharedConfig.currentProxy = pick;

            if (SharedConfig.proxyList.size() > 1) {
                // Let Telegram's own rotation fail over when a proxy gets blocked.
                SharedConfig.proxyRotationEnabled = true;
                SharedConfig.saveConfig();
            }

            prefs.edit()
                    .putString("proxy_ip", pick.address)
                    .putInt("proxy_port", pick.port)
                    .putString("proxy_user", pick.username)
                    .putString("proxy_pass", pick.password)
                    .putString("proxy_secret", pick.secret)
                    .putBoolean("proxy_enabled", true)
                    .putBoolean(PREF_APPLIED, true)
                    .putString(PREF_APPLIED_ENDPOINT, pick.address + ":" + pick.port)
                    .apply();

            ConnectionsManager.setProxySettings(true, pick.address, pick.port, pick.username, pick.password, pick.secret);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            FileLog.d("RegionProxyManager: applied " + pick.address + ":" + pick.port + " (pool=" + SharedConfig.proxyList.size() + ")");
            // Arm the dead-pool watchdog: if we're still not connected through this proxy in WATCHDOG_MS,
            // it disables our auto-proxy so the user drops to direct instead of freezing on "Connecting…".
            AndroidUtilities.runOnUIThread(RegionProxyManager::watchdogCheck, WATCHDOG_MS);
        } catch (Throwable ignore) {
        } finally {
            running = false;
        }
    }

    /**
     * Remote kill switch. The owner set ru_proxy_active=false, so tear down the proxy WE auto-applied.
     * Runs on the main thread (where proxy settings are otherwise mutated). Safety rails:
     *  - only acts when a proxy is actually enabled;
     *  - only touches a proxy WE applied ({@link #PREF_APPLIED}) whose endpoint still matches the one we
     *    set — if the user has since switched to their own proxy, the endpoints differ and we leave it be;
     *  - clears {@link #PREF_APPLIED} so a later re-grant (ru_proxy_active=true) is free to reconnect. That
     *    is the one thing separating our kill from a USER turn-off: a user turn-off keeps PREF_APPLIED set
     *    and stays locked off, while our kill clears it so the pool can come back once it is healthy again.
     */
    private static void killOnMain() {
        try {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            if (!prefs.getBoolean("proxy_enabled", false)) {
                return; // nothing enabled to kill
            }
            if (!prefs.getBoolean(PREF_APPLIED, false)) {
                return; // the active proxy is the user's own — never kill it
            }
            String applied = prefs.getString(PREF_APPLIED_ENDPOINT, "");
            String current = prefs.getString("proxy_ip", "") + ":" + prefs.getInt("proxy_port", 0);
            if (TextUtils.isEmpty(applied) || !applied.equals(current)) {
                return; // the user switched to a different proxy since we applied — leave it alone
            }
            prefs.edit()
                    .putBoolean("proxy_enabled", false)
                    .putBoolean("proxy_enabled_calls", false)
                    .putBoolean(PREF_APPLIED, false)
                    .remove(PREF_APPLIED_ENDPOINT)
                    .apply();
            ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            FileLog.d("RegionProxyManager: kill switch disabled auto-proxy " + current);
        } catch (Throwable ignore) {
        } finally {
            running = false;
        }
    }

    /**
     * Dead-pool watchdog — the safety net that stops a bad pool from trapping a Russian user at
     * "Connecting to proxy…". {@link #WATCHDOG_MS} after we auto-applied a proxy, if the app STILL has not
     * connected through it, we disable our auto-proxy so the user drops to a direct connection, and set a
     * {@link #BACKOFF_MS} cooldown so we don't re-attach the same dead pool on the next launch. Safety rails
     * mirror the kill switch: acts only while a proxy is enabled, only on the exact endpoint WE applied
     * (never a user's own proxy), and only when the failure is the proxy's fault — a Connected/Updating state
     * means it works, and WaitingForNetwork means there's simply no internet (not the proxy) so we leave it.
     */
    private static void watchdogCheck() {
        try {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            if (!prefs.getBoolean("proxy_enabled", false)) {
                return; // already disabled (user or kill switch)
            }
            if (!prefs.getBoolean(PREF_APPLIED, false)) {
                return; // the active proxy is the user's own — never touch it
            }
            String applied = prefs.getString(PREF_APPLIED_ENDPOINT, "");
            String current = prefs.getString("proxy_ip", "") + ":" + prefs.getInt("proxy_port", 0);
            if (TextUtils.isEmpty(applied) || !applied.equals(current)) {
                return; // the user switched to a different proxy since we applied — leave it alone
            }
            int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateConnected
                    || state == ConnectionsManager.ConnectionStateUpdating
                    || state == ConnectionsManager.ConnectionStateWaitingForNetwork) {
                return; // proxy works, or there is no network at all — not a dead-pool situation
            }
            // Still stuck connecting through the proxy after the grace period → treat the pool as dead.
            prefs.edit()
                    .putBoolean("proxy_enabled", false)
                    .putBoolean("proxy_enabled_calls", false)
                    .putBoolean(PREF_APPLIED, false)
                    .remove(PREF_APPLIED_ENDPOINT)
                    .putLong(PREF_BACKOFF_UNTIL, System.currentTimeMillis() + BACKOFF_MS)
                    .apply();
            if (SharedConfig.proxyRotationEnabled) {
                SharedConfig.proxyRotationEnabled = false;
                SharedConfig.saveConfig();
            }
            ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            FileLog.d("RegionProxyManager: watchdog — pool dead (state=" + state + "), disabled auto-proxy + backing off " + current);
        } catch (Throwable ignore) {
        }
    }

    private static String key(SharedConfig.ProxyInfo p) {
        return p.address + ":" + p.port + ":" + p.username + ":" + p.password + ":" + p.secret;
    }
}
