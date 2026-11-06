# BMS Monitor: JBD battery dashboard for Android tablets

An Android app that stays connected over Bluetooth to **three JBD (Jiabaida / "Xiaoxiang") smart BMS units at the same time** and shows them side by side on one screen.

## What it shows

**Home screen (all 3 batteries side by side)**
- State of charge gauge, remaining / nominal Ah
- Charging / discharging / idle with the current in amps (+ charging, − discharging)
- Pack voltage, power (W), time to full / time to empty
- Temperature, cycle count, cell voltage difference
- Every cell voltage, with the lowest and highest cells highlighted and balancing cells marked
- Charge and discharge MOSFET state
- Active alarms (the card gets a red or amber border)
- A bank summary bar with combined charge, current, power, remaining capacity and time estimate

**Battery detail screen** (tap a card): per-cell voltage bars, every BMS protection flag, each temperature sensor, hardware and software version, manufacture date, signal strength, reconnect count.

## Siren

For a BMS fault, the app sounds a **loud wailing siren that keeps going until you press Silence**:
- The siren plays through the **alarm** volume, like an alarm clock, so it still sounds in silent mode. By default it turns the alarm volume up to maximum while it sounds and puts it back afterwards. Phones also vibrate.
- A full-screen red alarm page wakes the screen, even when it's locked, and shows which battery has which fault, with a big **SILENCE ALARM** button. The notification has a **Silence** button too.
- The siren keeps sounding even if the fault clears, until someone silences it. A *new* fault, or a fault that clears and comes back, sets it off again.
- It sounds for BMS protections, a switched-off discharge MOSFET and high temperature. You can turn on **Siren for warnings too** to include low charge, low temperature, cell imbalance and a battery that stays disconnected.
- Use **Settings → Siren → Test siren** to hear it.

## Alarms

The app raises a notification with sound when an alarm starts:
- **BMS protections:** cell/pack over- and under-voltage, charge/discharge over-current, short circuit, charge/discharge over- and under-temperature, front-end IC error, MOSFET lock
- Discharge MOSFET off (no output) / charge MOSFET off
- Low state of charge, high or low temperature, cell imbalance, and a battery that stays disconnected. You can change these thresholds or turn them off in Settings.

## Staying connected

- A foreground service holds all three Bluetooth connections, so they stay up when the app is in the background or the screen is off. A status notification shows the live numbers.
- If a battery drops out (out of range, BMS reboot, Bluetooth toggled), the app keeps retrying. A background scan spots the BMS as soon as it advertises again, and the app reconnects straight away.
- Optional settings: keep the screen on, keep the CPU awake so polling continues with the screen off, and start automatically when the tablet boots.
- For the most reliable connection, tap **Allow** on the "run in the background without restrictions" prompt.

## Installing

Every push to this folder builds a signed APK with GitHub Actions and publishes it as a release.

1. On the tablet, open
   **https://github.com/robert-grant-0/robert-grant-0.github.io/releases/latest/download/BMS-Monitor.apk**
2. Open the downloaded file. If Android asks, allow your browser to install unknown apps.
3. Start **BMS Monitor**, allow Bluetooth and notification access, then tap each empty battery card to pick its BMS.

### Updates

Until you set up a permanent signing key, each build is signed with a temporary key. To install a newer build, uninstall the old app first. Your battery assignments are lost, so pick the three BMS units again.

To make updates install straight over the old version, set up a key once:

1. On any computer with Java installed, run
   `keytool -genkeypair -keystore bms.keystore -alias bms -keyalg RSA -keysize 2048 -validity 10000`
   and choose a password.
2. Run `base64 -w0 bms.keystore` (on macOS: `base64 -i bms.keystore`) and copy the output.
3. On GitHub, open the repository's **Settings → Secrets and variables → Actions** and add these secrets:
   - `BMS_KEYSTORE_BASE64`: the base64 text
   - `BMS_KEYSTORE_PASSWORD`: the password
   - `BMS_KEY_ALIAS`: `bms`
4. Keep `bms.keystore` somewhere safe and never commit it.

> A JBD BMS accepts only one Bluetooth connection at a time. Close the Xiaoxiang / JBD app on any phone that is connected to the batteries, or they won't show up.

Requirements: Android 8.0 or newer with Bluetooth LE. Phones work too, with the cards stacked vertically.

## Building it yourself

Open this folder in Android Studio, or run:

```
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
./gradlew testReleaseUnitTest # protocol parser tests
```

## Code layout

| Path | What |
| --- | --- |
| `bms/JbdProtocol.kt` | JBD frame building, reassembly from 20-byte BLE packets, checksum, parsing (pure Kotlin, unit tested) |
| `bms/GattSession.kt` | One BLE GATT connection wrapped in coroutines |
| `bms/BmsConnection.kt` | Connect, poll and reconnect loop for one battery |
| `bms/DeviceFinder.kt` | Shared background scan that finds disconnected batteries |
| `bms/BmsManager.kt` | Owns the three connections and exposes their state |
| `bms/Alarms.kt` | Alarm rules |
| `service/BmsService.kt` | Foreground service, status notification, alarm notifications, wake lock |
| `ui/` | Jetpack Compose screens |

Protocol: service `FF00`, notify `FF01`, write `FF02`. The app sends `DD A5 03 00 FF FD 77` (basic info) and `DD A5 04 00 FF FC 77` (cell voltages) every poll interval (2 s by default). The app only reads from the BMS and never changes BMS settings.
