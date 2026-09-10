# Gemini MDIU

A working recreation of the **Gemini spacecraft Manual Data Insertion Unit (MDIU)** for Android and the web.

The historical panel models the two crew units:

- **MDR — Manual Data Readout:** seven decimal positions, split into 2-digit `ADDRESS` and 5-digit `MESSAGE`, plus `READ OUT`, `CLEAR`, `ENTER`, and `PWR` controls.
- **MDK — Manual Data Keyboard:** decimal keys `0` through `9`, with the separate wide `ZERO` key.

## 0.2.0

- Adds **CLOCK** mode: all seven wheels show `HHMMSSd` in local 24-hour time (`d` = tenths of a second).
- Adds **DREAM** mode: a deliberately non-historical ambient/self-test animation. It is not presented as Gemini mission telemetry.
- Adds **DIM** mode.
- Web build adds a Full Screen control and keyboard shortcuts.
- Modern controls are outside the historical MDIU panel so normal MDIU operation remains visually distinct.

## MDIU behavior implemented

- `CLEAR` starts a new entry and is required before each operation.
- Two digits select a logical address (`01`–`99` in the current interface model).
- Two address digits + five message digits + `ENTER` stores a value.
- Two address digits + `READ OUT` recalls the stored value.
- Invalid sequences drive the seven-digit readout to `0000000`.
- A 500 ms digit cadence is modeled.
- Stored values persist locally on both platforms.
- Pressing any historical MDIU control while CLOCK/DREAM is active returns immediately to MDIU mode.

## OBC emulator status

The display/interface runs now. A Gemini OBC emulator implementation also exists in the Virtual AGC project (`yaOBC`), and the next integration layer is to connect the MDIU to an OBC execution core rather than treating addresses as generic persisted memory.

This repository does **not** claim to contain recovered Gemini mission flight software. Historical Gemini software surviving in public sources is much sparser than Apollo AGC software. Any reconstructed or diagnostic program must remain clearly identified as such.

## Web

Open `web/index.html` from a web server, or deploy the `web/` directory. It works offline after the service worker has cached the release.

```bash
cd web
python3 -m http.server 8080
```

Keyboard shortcuts: digits `0`–`9`, `C` = CLEAR, `R` = READ OUT, `Enter` = ENTER, `P` = PWR, `K` = CLOCK, `D` = DREAM, `M` = MDIU.

## Android

The Android implementation is a native `Canvas`-rendered app with no third-party runtime dependencies or Gradle requirement.

```bash
cd android
ANDROID_SDK_ROOT=/path/to/sdk ./build_sdk.sh
```

The script expects:

- `platforms/android-37/android.jar`
- `build-tools/36.0.0/aapt2`, `d8`, `zipalign`, and `apksigner`
- JDK `javac`, `jar`, and `keytool`

Output: `android/dist/Gemini-MDIU.apk`.

### Verified local build

Version 0.2.0 has been compiled with Android Platform 37 and Build Tools 36.0.0. The Java MDIU model test passes and the generated APK verifies with Android APK signature schemes v1, v2, and v3.

## Historical basis

Layout and operating behavior are based on Project Gemini documentation describing the MDK/MDR and surviving hardware photographs. The recreation is drawn from scratch rather than embedding museum imagery.
