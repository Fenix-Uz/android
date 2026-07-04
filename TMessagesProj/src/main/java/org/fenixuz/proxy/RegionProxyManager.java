package org.fenixuz.proxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
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
 *  - never touches anything if the user already enabled a proxy themselves;
 *  - can be disabled by the user via {@link #PREF_AUTO};
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
    /** Remote Config key: when true, apply regardless of region — for testing / global rollout. */
    private static final String RC_FORCE = "proxy_force_all";
    /** User switch for Novagram's auto-proxy (default on). */
    public static final String PREF_AUTO = "novagram_region_proxy_auto";
    /** Marks that we, not the user, turned the current proxy on. */
    public static final String PREF_APPLIED = "novagram_region_proxy_applied";

    private static final long FETCH_TIMEOUT_SECONDS = 15;

    private static volatile boolean running;

    private RegionProxyManager() {
    }

    /**
     * Entry point — call once, early in startup. Cheap and safe to call more than once; it no-ops
     * unless the user hasn't set up their own proxy and we're not already working. The real
     * region/force decision is made after the Remote Config fetch.
     */
    public static void maybeAutoConnect() {
        if (running) {
            return;
        }
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        if (!prefs.getBoolean(PREF_AUTO, true)) {
            return; // user opted out of Novagram auto-proxy
        }
        if (prefs.getBoolean("proxy_enabled", false)) {
            return; // a proxy is already active (user's own or ours from a previous run) — leave it
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
            defaults.put(RC_FORCE, false);
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

            boolean force = rc.getBoolean(RC_FORCE);
            ArrayList<String> countries = deviceCountries();
            boolean allowed = force || regionAllowed(rc.getString(RC_REGIONS), countries);
            FileLog.d("RegionProxyManager: countries=" + countries + " force=" + force + " allowed=" + allowed);
            if (!allowed) {
                running = false;
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
     * All country signals the device exposes — SIM country, the registered network country (this is the
     * one that flips to the VISITED country while roaming), and the device region setting. Lower-cased,
     * de-duplicated, empties dropped. We collect ALL of them (not just the first) so that e.g. a foreign
     * SIM roaming on a Russian network still counts as "in Russia". Login-free, no permission, offline.
     */
    private static ArrayList<String> deviceCountries() {
        ArrayList<String> out = new ArrayList<>();
        try {
            Context ctx = ApplicationLoader.applicationContext;
            TelephonyManager tm = ctx == null ? null : (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                addCountry(out, tm.getSimCountryIso());
                addCountry(out, tm.getNetworkCountryIso());
            }
        } catch (Throwable ignore) {
        }
        try {
            addCountry(out, Locale.getDefault().getCountry());
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
                return; // user enabled a proxy while we were fetching — don't override
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
                    .apply();

            ConnectionsManager.setProxySettings(true, pick.address, pick.port, pick.username, pick.password, pick.secret);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            FileLog.d("RegionProxyManager: applied " + pick.address + ":" + pick.port + " (pool=" + SharedConfig.proxyList.size() + ")");
        } catch (Throwable ignore) {
        } finally {
            running = false;
        }
    }

    private static String key(SharedConfig.ProxyInfo p) {
        return p.address + ":" + p.port + ":" + p.username + ":" + p.password + ":" + p.secret;
    }
}
