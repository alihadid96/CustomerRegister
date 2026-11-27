# CustomerRegister

Offline Android app for managing customer subscriptions: automatic expiration dates, reminders before expiry, renewals with full history, WhatsApp Click-to-Chat, CSV/Excel export, and backup/restore. English and Arabic (RTL), light and dark themes.

| | |
|---|---|
| Package | `com.customerregister.app` |
| Version | 1.0.0 (versionCode 1) |
| Min / target SDK | Android 8.0 (API 26) / Android 15 (API 35) |
| Stack | Kotlin 2.1, Jetpack Compose + Material 3, Room, WorkManager, DataStore, Navigation Compose, MVVM, Coroutines/Flow |

---

## Important: this APK was not built here

The project was written in a sandbox that could not reach Google's Maven repository, Maven Central or the Android SDK servers. Without them, Gradle can't download the Android Gradle Plugin, Compose, Room or WorkManager. So:

- **No APK is included.** None was faked.
- The domain logic (expiry math, statuses, reminder schedule, duplicate guard, WhatsApp templates, phone normalisation, CSV, XLSX) **was compiled and run**: 22 unit tests, all passing (see *Testing* below). The generated XLSX file was also opened with a real spreadsheet library.
- The Android/Compose layer was syntax-checked with the Kotlin compiler and reviewed by hand. It still needs its first real Gradle build, and that build may turn up small compile errors. If it does, paste the error and I'll fix it.

Choose **one** of the two methods below to get the signed APK.

---

## Method A: build in the cloud with GitHub (no Android Studio needed)

1. Create a **private** repository on github.com, for example `CustomerRegister`.
2. Upload the *contents* of this folder (not the ZIP itself). Make sure `.github/workflows/build-apk.yml`, `gradlew` and `gradle/wrapper/gradle-wrapper.jar` are included. The keystore files are excluded by `.gitignore` on purpose.
3. In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add the four secrets listed in `keystore/SECRETS.txt`:
   `CR_KEYSTORE_BASE64`, `CR_KEYSTORE_PASSWORD`, `CR_KEY_ALIAS`, `CR_KEY_PASSWORD`.
4. Open the **Actions** tab → **Build release APK** → **Run workflow**. The workflow also runs automatically on every push to `main`.
5. When the run is green (about 5–8 minutes), open it and download **CustomerRegister-release-apk** under *Artifacts*. Unzip it to get `CustomerRegister-v1.0.0.apk`.

The workflow runs the unit tests, builds the signed release APK, and verifies its signature with `apksigner`.

## Method B: build with Android Studio

1. Install Android Studio (Ladybug 2024.2 or newer).
2. **File → Open** this folder and let Gradle sync. It downloads the SDK 35 platform automatically.
3. `keystore.properties` and `keystore/customerregister-release.jks` are already in place, so release signing is configured.
4. Build the APK:
   - **Build → Generate Signed App Bundle / APK → APK → release**, or
   - from the terminal: `./gradlew assembleRelease` (on Windows: `gradlew.bat assembleRelease`).
5. The APK is written to `app/build/outputs/apk/release/app-release.apk`.

---

## Installing on your phone

1. Copy the APK to the phone (USB, Telegram/WhatsApp to yourself, Google Drive…).
2. Open it from the Files app. Android will ask to allow **Install unknown apps** for that source; allow it.
3. Tap **Install**, then **Open**.
4. On first launch, allow **notifications** (Android 13+).
5. Recommended: **Settings → Reminder reliability → Open battery settings** and set CustomerRegister to *Unrestricted / Don't optimise*. On Xiaomi, Huawei, Oppo and Vivo phones, also enable *Autostart* for the app.

---

## Release keystore (keep it safe)

- `keystore/customerregister-release.jks` (PKCS12), alias `customerregister`.
- Passwords are in `keystore.properties` and `keystore/SECRETS.txt`.
- SHA-256 certificate: `94:D8:43:E3:87:2D:7D:6D:06:98:3B:EB:8F:DF:6E:2E:AE:5B:B9:ED:19:1C:89:A7:5F:6B:E8:D2:B1:8C:B3:D7`
- **Back up these files.** Android only installs an update over the existing app if it is signed with the same key, so if you lose the keystore you will have to uninstall the app (and lose its data unless you made an in-app backup).
- For a new version, raise `versionCode` and `versionName` in `app/build.gradle.kts`.

---

## How reminders work

- Defaults are **7, 3 and 1 days before expiry, plus the expiration date**, at **09:00**. You can change the intervals, add custom ones (1–365 days), switch off the expiration-day reminder, change the time, choose a sound, or turn reminders off completely.
- Each customer has **their own WorkManager job** (`reminder_<id>`). It wakes at that customer's next reminder time, posts the notification, and schedules the following one. Adding, editing or renewing a customer rebuilds their job; deleting a customer cancels it.
- A **safety-net job** runs every 12 hours and catches anything Android delayed.
- **After a reboot, app update, clock change or timezone change**, every schedule is rebuilt (WorkManager also keeps its jobs across reboots on its own).
- **No duplicates:** before posting, each reminder is recorded in the Room table `notification_log` under a UNIQUE key (customer, expiration date, interval). If that key already exists, nothing is posted. Clearing the history only hides entries, so cleared reminders are not sent again. A renewal creates a new expiration date, so a fresh set of reminders begins.
- **Missed reminders:** if the phone was off on a reminder day, the closest missed reminder is shown as soon as possible with the real number of days remaining. Older missed reminders are not repeated.
- **Exact alarms are not used**, so no special permission is needed. The trade-off is that Android can delay a reminder a little.

### Android battery limitations

Android's Doze mode and App Standby can postpone background work, typically by minutes and sometimes by a few hours on an idle phone. Some manufacturers go further and kill background apps. CustomerRegister copes with this in three ways: it catches up as soon as Android lets it run, the safety-net job adds a second chance, and the Settings screen shows whether battery optimisation is restricting the app, with a button to change it. For reminders that arrive on time, set the app to *Unrestricted* battery use.

---

## WhatsApp

- Every customer card and profile has a WhatsApp button. Notifications have one too.
- The prepared message opens in an editable dialog first. Then WhatsApp opens through official Click-to-Chat (`https://wa.me/<number>?text=…`). **Nothing is sent automatically. You press Send in WhatsApp.**
- WhatsApp Business is used if it is installed, otherwise regular WhatsApp, otherwise the browser.
- Templates can be edited in Settings. Variables: `{name}`, `{id}`, `{expiry_date}`, `{remaining_days}`. There are separate templates for reminders and for renewal confirmations.
- Phone numbers are stored in international format. A number typed with a leading `0` gets the default country code (Settings → WhatsApp, default `+963`).

## Data, export and backup

- Everything is stored locally in Room/SQLite. No account, cloud service or internet connection is needed.
- **Export CSV** (UTF-8 with BOM, so Excel shows Arabic correctly) and **Export Excel (.xlsx)** with two sheets, *Customers* and *Subscription history*.
- **Backup** writes one JSON file containing customers, the full renewal history, notification history and settings. **Restore** shows what the file contains and asks for confirmation before replacing anything. Reminders are rescheduled after a restore.
- Android cloud auto-backup is disabled on purpose, to keep customer data private. Use the in-app backup and keep the file somewhere safe.
- Database migrations: the schema is exported to `app/schemas/`. Destructive migration is never enabled, so an app update can't silently wipe data.

---

## Testing

### Automated (ran here: 22/22 passed)

`app/src/test/.../domain/`
- `SubscriptionMathTest`: duration math for days, weeks, months and years, month-end clamping, leap years, remaining days, configurable thresholds, renewal from the current expiry versus from today.
- `ReminderPlannerTest`: all four default reminders fire on their day at the set time; nothing fires before that time; catch-up after the phone was off; nothing after expiry; the expiration-day toggle; the full schedule walk 7 → 3 → 1 → 0; duplicate-key stability; a renewal starting a new schedule.
- `MessageAndExportTest`: template variables, phone normalisation, CSV quoting and formula-injection guard, and XLSX package structure and escaping.

Run them with `./gradlew test`. CI runs them on every build.

### Manual checklist (needs a real phone or emulator; not done here)

- [ ] Add a customer: ID is 1001, 1002…; expiry is calculated; it appears on the Dashboard.
- [ ] Edit name, phone, dates; delete with confirmation (its pending notification is cancelled).
- [ ] Search by name, ID or phone; sort; filter Active / Expiring / Expired.
- [ ] Reminders: add a customer expiring in 3 days with the reminder time set a minute ahead, then close the app. The notification arrives, shows name, ID, date and days remaining, and **Open profile** opens the customer.
- [ ] Reboot the phone; reminders still arrive. Change the timezone; times follow the new zone.
- [ ] Renew: a history entry is added, the date updates, the WhatsApp confirmation opens.
- [ ] WhatsApp opens with the number and message filled in; Business is preferred when installed.
- [ ] Backup → delete a customer → Restore → everything is back.
- [ ] Light, dark and system themes; English and Arabic (layout flips to RTL).
- [ ] Close and reopen the app; all data is still there.

---

## Project layout

```
app/src/main/java/com/customerregister/app/
  CustomerRegisterApp.kt     Application + dependency container
  MainActivity.kt            entry point, permissions, notification deep links
  domain/                    pure logic: SubscriptionMath, ReminderPlanner, MessageTemplate, Exporters
  data/                      Room (db/), SettingsRepository (DataStore), CustomerRepository, BackupManager
  notifications/             NotificationHelper, ReminderScheduler + workers + boot/time receiver
  ui/                        theme, components, ViewModels, AppRoot (navigation), screens/
  util/                      date formats, localisation, WhatsApp intents
app/src/main/res/values{,-ar}/strings.xml   generated by scripts/gen_strings.py (keeps EN/AR in sync)
.github/workflows/build-apk.yml             cloud build of the signed release APK
```
