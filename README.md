# RelayGram — SMS to Telegram Forwarder

An Android app that automatically forwards incoming SMS messages to a Telegram bot in real time.

Built for personal use — ideal for monitoring OTP, bank alerts, and important messages remotely.

---

## Features

- Forward all SMS or filter by sender and keyword
- Smart OTP detection — tap to copy in Telegram
- Background service with auto-restart on reboot
- Network monitoring — resumes forwarding when connection restores
- Save all settings once — persistent across restarts
- Dark mode support
- Minimal, clean UI

---

## How It Works

```
SMS arrives on phone
       ↓
App detects and filters
       ↓
Sends to Telegram bot
       ↓
You receive it anywhere
```

---

## Setup

### 1. Create a Telegram Bot

- Open Telegram and search for `@BotFather`
- Send `/newbot` and follow the steps
- Copy the **Bot Token**

### 2. Get Your Chat ID

- Send any message to your bot
- Open this URL in browser:
  `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates`
- Find `"id"` inside `"chat"` — that is your Chat ID

### 3. Configure the App

- Enter Bot Token and Chat ID
- Choose Forward Mode:
  - **All SMS** — forwards everything
  - **Filter** — forwards only matching SMS
- Add Senders (e.g. `GP, DBBL, BKASH`)
- Add Keywords (e.g. `OTP, BANK, debit`)
- Tap **Save**
- Tap **Start Forwarding**

---

## Telegram Message Format

```
New SMS

From: AD-DBBL
Message:
Your OTP is 123456. Valid for 5 minutes.

OTP: 123456

Time: 03:45 PM
Date: Jun 12, 2026
```

OTP is displayed as a tappable code — one tap to copy.

---

## Build

This project uses GitHub Actions. No Android Studio required.

**Actions → Build RelayGram → Run workflow → Select:**

| Option | Output |
|--------|--------|
| `debug` | Debug APK (no signing needed) |
| `release` | Signed Release APK |

Download from **Actions → latest run → Artifacts**.

---

## GitHub Secrets (for release build)

`Settings → Secrets → Actions → New secret`:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w 0 android.keystore` |
| `STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

---

## Project Structure

```
app/src/main/
├── java/com/kamildex/relaygram/
│   ├── MainActivity.kt          — UI and controls
│   ├── SmsReceiver.kt           — Detects incoming SMS
│   ├── SmsForwarderService.kt   — Background foreground service
│   ├── SmsQueue.kt              — Queue to avoid rate limiting
│   ├── TelegramSender.kt        — Sends messages to Telegram
│   ├── BootReceiver.kt          — Auto-start on reboot
│   ├── LogAdapter.kt            — Recent messages list
│   ├── SmsLog.kt                — Local log storage
│   └── Prefs.kt                 — Settings storage
├── res/layout/
│   ├── activity_main.xml
│   └── item_log.xml
└── AndroidManifest.xml
```

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `RECEIVE_SMS` | Detect incoming SMS |
| `READ_SMS` | Read message content |
| `INTERNET` | Send to Telegram |
| `FOREGROUND_SERVICE` | Run in background |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on reboot |
| `POST_NOTIFICATIONS` | Show service notification |

---

## Notes

- This app is intended for personal use only
- Do not use to intercept others' messages
- Not available on Google Play Store

---

## Developer

**Kamil Hussen**
[kamildex.com](https://kamildex.com)

---

MIT License — Free to use and modify.
