# Aero Tracker — offline phone-side prototype

This project is the phone-side prototype for the Aero Space Defense System.

## What it does
- Connects to an MJPEG stream from the ESP32-CAM over the local Wi-Fi network.
- Extracts JPEG frames locally.
- Runs Google's bundled ML Kit Object Detection in STREAM_MODE.
- Uses the detector's tracking ID and bounding box.
- Calculates the target center as normalized X/Y.
- Produces LEFT/RIGHT/CENTER and UP/DOWN decisions using a 40/60% dead-zone.
- Contains manual command buttons for testing.
- Sends HTTP commands to the ESP32-CAM.

## Important
The ESP32-CAM command paths are **placeholders**:
`/left`, `/right`, `/center`, `/up`, `/down`

Tomorrow, replace those constants with the actual requests used by the existing webpage. Do not guess them.

The default stream URL is also a placeholder:
`http://192.168.4.1:81/stream`

If the existing firmware uses a different stream URL, change it in the app.

## Offline behavior
The app uses:
`com.google.mlkit:object-detection:17.0.2`

Google's current documentation lists this as the bundled object-detection feature. Once the APK is built and installed, the detector does not need an Internet connection at runtime.

## Build tonight
Open the project in Android Studio or a compatible Android Gradle IDE while Internet is available, let Gradle download dependencies, and build/install the APK.

Do NOT rely on an Internet connection at school.

## Safety / project scope
This app is intended for harmless object tracking and pan/tilt demonstration only. It contains no targeting or harmful payload control.
