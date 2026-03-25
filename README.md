# SpeakerEQ

Android app that measures your Bluetooth speaker's frequency response via the phone microphone and applies a system-wide EQ correction to linearize the sound.

## Features

- **Frequency measurement** – Sine sweep or pink noise test signal, recorded with the phone microphone
- **FFT analysis** – 31-band 1/3 octave analysis (20 Hz – 20 kHz) with Hann windowing
- **System-wide EQ** – Correction applied via `android.media.audiofx.Equalizer` on audio session 0
- **Speaker profiles** – Profiles stored in a Room database, linked to Bluetooth device MAC address
- **Auto-load** – Correct profile loaded automatically when the paired speaker connects
- **Level check** – Pre-measurement check ensures mic input is sufficient before capturing
- **Averaging** – 1×, 3×, or 5× measurements averaged for a more stable result

## Requirements

- Android 8.0 (API 26) or later
- Bluetooth speaker
- Microphone permission

## Build

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/`.

## How it works

1. Connect your Bluetooth speaker.
2. Tap **Measure** and place the phone ~30 cm in front of the speaker.
3. The app plays a test signal and records the response.
4. A correction curve (inverse of the measured response) is calculated and applied as EQ.
5. Save the profile — it reloads automatically next time the speaker connects.
