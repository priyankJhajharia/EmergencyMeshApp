
# EmergencyMesh

An offline-first Android app for emergency communication when there's no internet or cellular signal. It connects nearby phones directly using Bluetooth, BLE, and WiFi Direct, and relays messages across multiple devices using a custom mesh networking protocol.

## Download

[⬇️ Download APK](https://github.com/priyankJhajharia/EmergencyMeshApp/releases/latest)

> Note: This app requires SMS, Bluetooth, and Location permissions for its core functionality, so it's distributed here directly rather than through the Play Store. Install requires enabling "Install from unknown sources" on Android.

## Why

In disasters, protests, remote areas, or crowded events, cellular networks often go down or get overloaded right when people need to reach each other most. EmergencyMesh lets nearby phones talk to each other directly — no router, no SIM card, no internet required.

## Features

- **Offline mesh chat** — send and receive messages between nearby devices over Bluetooth or WiFi Direct, with messages relaying through intermediate devices to extend range
- **Per-device conversations** — chat history is tracked separately for each connected peer, like a normal messaging app
- **SOS alert** — hold a button or shake the phone 3 times to broadcast an emergency alert with your GPS location (as a tappable Google Maps link) to every nearby device
- **5-second cancel window** — shake again during the SOS countdown to cancel an accidental trigger
- **SMS fallback** — automatically texts your saved emergency contacts when you trigger SOS and cellular signal is available
- **SMS-triggered alarm** — if an emergency contact sends you an SOS text from this app, your phone plays a loud siren (bypassing silent mode and Do Not Disturb) and shows a notification with a one-tap "Open Maps" button
- **Runs in the background permanently** — a foreground service keeps the mesh alive even after closing the app, auto-restarts if killed, and auto-starts on phone boot, so you never need to manually open the app after initial setup

## How It Works

```
Phone A ◄── Bluetooth / WiFi Direct ──► Phone B ◄── Bluetooth / WiFi Direct ──► Phone C
```

Each message carries a TTL (time-to-live) and hop count. When a device receives a message it hasn't seen before, it stores it, displays it, decrements the TTL, and forwards it to its own connected peers — allowing messages (especially SOS alerts) to travel beyond direct range through a chain of devices.

- **Bluetooth RFCOMM** — primary transport for paired devices; also supports connecting to devices discovered via BLE by falling back to their classic Bluetooth MAC address
- **BLE (Bluetooth Low Energy)** — used for lightweight discovery/advertising of nearby devices before a full connection is made
- **WiFi Direct (P2P)** — used for faster, pairing-free connections between strangers; one device becomes the Group Owner and the other connects as a client over a persistent TCP socket

## Tech Stack

Kotlin · Android SDK (API 26–35) · Room Database · Kotlin Coroutines & Flow · Bluetooth Classic (RFCOMM) · Bluetooth LE · WiFi Direct (P2P) · TCP Sockets · Foreground Services · Broadcast Receivers · Notifications API · Location Services · SMS API · Accelerometer Sensor API · AudioTrack (programmatic audio synthesis)

## Project Structure

```
data/        Room entities and DAOs (messages, contacts)
bluetooth/   Classic Bluetooth (RFCOMM) and BLE managers
wifi/        WiFi Direct peer discovery and TCP socket messaging
mesh/        Mesh routing logic, TTL/hop-count, location & SMS helpers
service/     Foreground service, shake detector, alarm/siren, notifications
ui/          Activities and RecyclerView adapters for chat, contacts, profile
```

## Known Limitations

- Classic Bluetooth connections still require Android's pairing flow on some devices
- WiFi Direct in this implementation supports one peer connection at a time
- Messages are not end-to-end encrypted
- SMS-related permissions make this app unsuitable for direct Play Store distribution without modification (Google restricts `RECEIVE_SMS` to default SMS apps)

## Setup

1. Clone the repo and open in Android Studio
2. Build and install on two or more Android devices (minimum SDK 26)
3. Grant Bluetooth, Location, SMS, and Notification permissions on first launch
4. Open the app on each device, then use **Find Peers** from the menu to connect
5. Optionally enable Autostart / disable battery optimization for this app in your phone's settings for fully reliable background operation (manufacturer-dependent)

## License

This project is for educational and portfolio purposes.
