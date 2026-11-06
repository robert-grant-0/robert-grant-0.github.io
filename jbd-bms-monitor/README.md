# BMS Monitor: JBD battery dashboard for Android tablets

An Android app that stays connected over Bluetooth to **three JBD (Jiabaida / "Xiaoxiang") smart BMS units at the same time** and shows them side by side on one screen.

## What it shows

**Bank summary (top of the home screen)**
- A state-of-charge ring for the whole bank, net power, current, voltage, remaining Ah and a "Full in…" / "Empty in…" estimate. With a SmartShunt these figures come from the shunt.
- An **energy flow** diagram: solar → batteries ← DC-DC & loads. Moving particles show which way the energy is going.
- A **power chart** of the last 3 hours: green above zero (charging), blue below (discharging), with solar as a gold line
- All three can be shown in **amps instead of watts**: Settings → Appearance → Energy flow in

**One card per battery (all 3 side by side)**
- A battery that fills to its state of charge, rippling while it charges
- Charging / discharging / idle, and the current in amps (+ charging, − discharging)
- Power, voltage and time to full or empty
- Temperature, cell spread, cycle count, and the battery's share of the bank current
- Cell voltage bars. They stay level for a healthy pack and highlight the highest and lowest cells only when the spread matters. Balancing cells get a gold dot.
- A status strip with alarms, signal strength and the charge/discharge MOSFET state. The card's outline turns amber or red for warnings and alarms.

**Battery detail screen** (tap a card): large cell bars with each voltage, every BMS protection flag, all temperature sensors, and the BMS's details.

**Design:** dark by default (light and automatic themes are in Settings), with the Inter typeface and figures that keep the same width as they change. There's an **Animations** switch for slower tablets.

## Victron SmartShunt and solar charger (optional)

The app reads a Victron **SmartShunt** and a **SmartSolar MPPT** from their Bluetooth *Instant Readout* broadcasts. It never connects to them, so VictronConnect keeps working and nothing on the Victron devices can be changed.

- **SmartShunt:** bank charge, current, power, voltage, Ah used and time to go are shown at the top of the screen. The shunt measures the whole bank, so these figures take priority over the BMS totals. Shunt alarms (low/high voltage, low charge, overload, …) feed into the app's alarms and siren.
- **Solar charger:** PV power, charging current, charge state (bulk/absorption/float) and today's yield. Charger errors raise an alarm.
- **Other (DC-DC − loads):** the shunt's current minus solar. With the engine running, this mostly shows what the DC-DC chargers add, minus whatever loads are on. The DC-DC chargers themselves can't be read directly.
- **Current sharing (parallel batteries):** each battery shows its share of the bank current. A warning appears when one battery carries noticeably more or less than the others for 2 minutes, which is often a loose terminal, a cable problem or a weak battery. A second warning fires if the shunt and the three BMS totals disagree for a minute.

Setup: **Settings → Victron devices → Choose**, then enter the device's encryption key. In VictronConnect, open the device, then ⚙ Settings → ⋮ → Product info → Instant readout details → Show. The key is stored only on the tablet, and the app has backups turned off so it isn't copied to the cloud.

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
