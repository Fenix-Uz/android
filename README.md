# Novagram

Novagram is an unofficial Telegram client for Android, developed by **VipAds LLC** in Uzbekistan.

It is a fork of [Telegram for Android](https://github.com/DrKLO/Telegram) that keeps the full messaging
core intact and adds a layer of privacy, convenience and local-market features on top of it.

- **Google Play** — [uz.codingtech.messengerapp](https://play.google.com/store/apps/details?id=uz.codingtech.messengerapp)
- **RuStore** — [org.messenger_pro.messenger](https://apps.rustore.ru/app/org.messenger_pro.messenger)
- **Website** — [novagram.org](https://novagram.org)

## What Novagram adds

Everything Telegram does, plus roughly 65 source files under `org.fenixuz` that implement:

**Privacy**
- **Ghost mode** — read messages, watch stories and stay offline without sending receipts or typing status, toggled from the chat toolbar.
- **Secret folder** — move chats into a hidden folder locked with a password, PIN or fingerprint. It is excluded from search, recents and suggestions.
- **Per-chat lock** — lock an individual chat behind the same credential.
- **Stranger shield** — filter unsolicited contact from people you do not know.
- **APK shield** — warn before opening APK files received in chats.

**Message history**
- **Edit history** — keep the earlier versions of messages someone edits, readable from the message menu.
- **Deleted-message saving** — keep messages that were deleted for everyone.

Both are stored on the device only; nothing is uploaded anywhere.

**Convenience**
- Auto-answer, auto-accept channel join requests, scheduled reminders.
- Chat preview bottom sheet, chat finder, custom folder icons, hideable tabs.
- Send confirmations for stickers, voice messages and GIFs.
- Photo-to-text (OCR) and voice-message translation.
- Send a gallery video as a round video note.
- Forward messages without revealing the original sender.
- QR and bot login flows.
- Up to 32 signed-in accounts, with per-account notification switches.

**Regional**
- Auto-proxy for censored regions, gated to physically Russian devices and driven entirely from Firebase Remote Config — no proxy credentials live in this repository.
- Sponsored-channel search results served by the VipAds backend.
- First-launch guided tour of the feature set, replayable from the settings toolbar.

## Building from source

### Requirements

- Android Studio (latest stable) or Gradle 8.7
- JDK 17 — required by Android Gradle Plugin 8.6.1
- Android SDK 36 (`compileSdk` / `targetSdk` 36, `minSdk` 21)
- NDK `27.2.12479018` — pinned; Gradle installs it for you
- Your own Telegram API credentials from [my.telegram.org](https://my.telegram.org)
- Your own Firebase project, for push notifications and Remote Config
- Your own release keystore

You cannot build this with our credentials, and you should not try to: Telegram bans `api_id`s that are
shared across clients, and a fork signed with someone else's key cannot update their app anyway.

### Setup

**1. Clone**

```bash
git clone https://github.com/Novagramorg/android.git
cd android
```

**2. Configure `local.properties`**

```bash
cp local.properties.example local.properties
```

```properties
sdk.dir=/path/to/your/Android/Sdk

# Telegram API credentials from https://my.telegram.org
TELEGRAM_APP_ID=your_app_id
TELEGRAM_APP_HASH=your_app_hash
```

**3. Configure `gradle.properties`**

```bash
cp gradle.properties.example gradle.properties
```

```properties
APP_PACKAGE=your.package.name
APP_VERSION_CODE=1
APP_VERSION_NAME=1.0.0

RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_password
RELEASE_STORE_PASSWORD=your_store_password
```

**4. Add `google-services.json`**

Create a Firebase project whose Android app uses your `APP_PACKAGE`, then drop the downloaded
`google-services.json` into every module you intend to build:

```
TMessagesProj/
TMessagesProj_App/
TMessagesProj_AppHockeyApp/
TMessagesProj_AppHuawei/
TMessagesProj_AppRustore/
TMessagesProj_AppStandalone/
```

`TMessagesProj_AppRustore` is a separate application id (`org.messenger_pro.messenger`) and therefore
needs its own Firebase app entry, not a copy of the Play one.

**5. Place your keystore**

```
TMessagesProj/config/release.keystore
```

**6. Build**

| Goal | Task | Output |
|---|---|---|
| Debug APK | `:TMessagesProj_App:assembleAfatDebug` | installable debug build |
| **Play Store bundle** | `:TMessagesProj_App:bundleBundleAfatRelease` | `bundleAfat` variant — this is the artifact you upload |
| Standalone / sideload APK | `:TMessagesProj_App:assembleAfatRelease` | `afat` variant |
| RuStore APK | `:TMessagesProj_AppRustore:assembleAfatRelease` | separate application id and version code |

> **Watch the variant names.** `bundleBundleAfatRelease` builds the `bundleAfat` flavor, while
> `bundleAfatRelease` builds the plain `afat` flavor. They are different products with different
> version codes — `APP_VERSION_CODE * 10 + 1` and `APP_VERSION_CODE * 10 + 9` respectively. Uploading
> the wrong one to Play burns a version code you cannot reuse.

Non-release build types are filtered to the `afat` flavor only, so `assembleAfatDebug` is the single
debug task.

The RuStore module versions itself independently, from `RUSTORE_VERSION_CODE` / `RUSTORE_VERSION_NAME`
in `local.properties` rather than from `APP_VERSION_CODE`. Leave them blank and it falls back to a
built-in default.

## Security

`local.properties`, `gradle.properties`, `google-services.json`, `agconnect-services.json` and any
`*.keystore` are gitignored and have never been committed to this repository. API credentials reach the
code only through `BuildConfig` fields generated at build time; none of them are hardcoded in source.
Proxy credentials live exclusively in Firebase Remote Config.

If you fork this project, keep it that way.

## License

Licensed under the **GNU General Public License v2.0**, the same license as the upstream Telegram for
Android source. See [LICENSE](LICENSE).

## Privacy Policy

See the [Privacy Policy](https://novagram.org/privacy) for details on how the app handles user data.

## Disclaimer

Novagram is an unofficial third-party client and is **not affiliated with, endorsed by, or sponsored
by** Telegram FZ-LLC or Telegram Messenger Inc. "Telegram" is a trademark of Telegram FZ-LLC.

## Credits

- Based on [Telegram for Android](https://github.com/DrKLO/Telegram) by Telegram FZ-LLC and contributors
- Maintained by **VipAds LLC**

## Contact

- **Email:** vipadsllc@gmail.com
- **Developer:** VipAds LLC, Tashkent, Uzbekistan
