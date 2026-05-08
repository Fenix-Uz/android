# FenixUz

FenixUz is an unofficial Telegram client for Android, developed by **VipAds LLC** in Uzbekistan.

## About

FenixUz is a fork of [Telegram for Android](https://github.com/DrKLO/Telegram), the official Telegram messenger application. It provides the same core messaging features under an alternative brand, distributed independently.

## License

This project is licensed under the **GNU General Public License v2.0 (GPLv2)**, the same license used by the original Telegram for Android source code. See the [LICENSE](LICENSE) file for details.

## Disclaimer

FenixUz is an unofficial third-party client and is **not affiliated with, endorsed by, or sponsored by** Telegram FZ-LLC or Telegram Messenger Inc. "Telegram" is a trademark of Telegram FZ-LLC.

## Privacy Policy

See [Privacy Policy](https://fenixuz.uz/privacy.html) for details on how the app handles user data.

## Building from Source

### Requirements

- Android Studio (latest stable)
- Android SDK 35
- NDK (installed automatically via Gradle)
- JDK 17
- Your own Telegram API credentials from [my.telegram.org](https://my.telegram.org)
- Your own Firebase project (for FCM push notifications)
- Your own release keystore for signing

### Setup Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Fenix-Uz/android.git
   cd android
   ```

2. **Configure `local.properties`:**

   Copy the example file and fill in your values:
   ```bash
   cp local.properties.example local.properties
   ```
   Then edit `local.properties`:
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   TELEGRAM_APP_ID=your_app_id
   TELEGRAM_APP_HASH=your_app_hash
   ```

3. **Configure `gradle.properties`:**

   Copy the example file and fill in your keystore credentials:
   ```bash
   cp gradle.properties.example gradle.properties
   ```
   Then edit `gradle.properties`:
   ```properties
   RELEASE_KEY_PASSWORD=your_password
   RELEASE_KEY_ALIAS=your_alias
   RELEASE_STORE_PASSWORD=your_store_password
   ```

4. **Add your `google-services.json`:**

   Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com) with your application package (e.g. `uz.codingtech.messengerapp`) and download `google-services.json` into the following directories:
   - `TMessagesProj/`
   - `TMessagesProj_App/`
   - `TMessagesProj_AppHockeyApp/`
   - `TMessagesProj_AppHuawei/`
   - `TMessagesProj_AppStandalone/`

5. **Place your `release.keystore`:**

   Generate your own keystore and place it at:
   ```
   TMessagesProj/config/release.keystore
   ```

6. **Build the project:**
   ```bash
   ./gradlew :TMessagesProj_App:assembleAfatRelease
   ```

   Or open the project in Android Studio and build via **Build → Build Bundle(s) / APK(s)**.

> **Note:** `local.properties`, `gradle.properties`, `google-services.json`, and `*.keystore` files are gitignored and must **never** be committed to version control.

## Credits

- Based on [Telegram for Android](https://github.com/DrKLO/Telegram) by Telegram FZ-LLC and contributors
- Maintained by **VipAds LLC**

## Contact

- **Email:** vipadsllc@gmail.com
- **Developer:** VipAds LLC, Tashkent, Uzbekistan
