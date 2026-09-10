# Gemini MDIU

A working recreation of the **Gemini spacecraft Manual Data Insertion Unit (MDIU)** for Android and the web.

The interface models the two physical crew units:

- **MDR — Manual Data Readout:** seven decimal positions, split into 2-digit `ADDRESS` and 5-digit `MESSAGE`, plus `READ OUT`, `CLEAR`, `ENTER`, and `PWR` controls.
- **MDK — Manual Data Keyboard:** `0` through `9`, with the historically separate wide `ZERO` key.

## Behavior implemented

- `CLEAR` starts a new entry and is required before each operation.
- Two digits select a logical address (`01`–`99` in this generic simulator).
- Two address digits + five message digits + `ENTER` stores a value.
- Two address digits + `READ OUT` recalls the stored value.
- Invalid sequences drive the seven-digit readout to `0000000`, matching the documented pilot-error indication.
- The first message digit may be `9`; Gemini conventions used that position to indicate a negative value for quantities that supported signed data.
- A 500 ms digit cadence models the documented MDIU display/entry timing.
- Stored values persist locally on both platforms.

This is an MDIU interface/computer-memory simulator, not yet a complete emulation of a specific Gemini mission's IBM onboard flight program. Mission-specific address tables and actual Gemini math-flow software can be added separately.

## Web

Open `web/index.html` from a web server, or deploy the `web/` directory. It can work offline after its first load.

For local testing:

```bash
cd web
python3 -m http.server 8080
```

Then open `http://localhost:8080/`.

Keyboard shortcuts: digits `0`–`9`, `C` = CLEAR, `R` = READ OUT, `Enter` = ENTER, `P` = PWR.

## Android

The Android implementation is a native `Canvas`-rendered app with no third-party dependencies or Gradle requirement.

```bash
cd android
ANDROID_SDK_ROOT=/path/to/sdk ./build_sdk.sh
```

The script expects:

- `platforms/android-37/android.jar`
- `build-tools/36.0.0/aapt2`, `d8`, `zipalign`, and `apksigner`
- JDK `javac`, `jar`, and `keytool`

Output: `android/dist/Gemini-MDIU.apk`.

## Historical basis

Layout and operating behavior are based on the Project Gemini Familiarization Manual (SEDR 300) descriptions of the MDK/MDR and surviving Smithsonian hardware photographs. The recreation is intentionally drawn from scratch rather than embedding museum imagery.
