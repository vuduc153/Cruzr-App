# Cruzr App

This repository contains the codebase for the Android app that runs on the Cruzr and Tiago robots. The app mainly handles teleconferencing functionality, with some additional robot-specific features in their corresponding branches.

* The `master` branch is for the **Cruzr robot**. It runs an embedded Websocket server that exposes APIs for controlling Cruzr-specific operations in addition to the video calling feature.
* The `tiago` branch is for the **Tiago robot**. It only includes the video calling functionality, with no additional robot-specific features.

In general, the code in the `tiago` branch is more stable than in the `master` branch, as later updates to the video call functions were not merged back into `master`.

## Compilation

The app can be compiled into an APK and installed on the robots' onboard tablets. The code has been developed and tested with the relatively outdated Gradle v8.6 and Android SDK 21 to ensure compatibility with the OS version running on the Cruzr robot. If you're working with a different build environment, you may need to downgrade your tools to match these versions in order to compile successfully.

## Cruzr App Details

### Cruzr SDK

The Cruzr app relies on the **Cruzr SDK version 0.1.8**, which is included in the `app/libs` directory of this repository. Documentation for the Cruzr SDK can be obtained upon request from Telstra.

### Deployment

To deploy the app to the Cruzr robot's onboard tablet, you will need a specific `adbkey` file. This key can also be obtained from Telstra upon request, as it is required for secure deployment via ADB (Android Debug Bridge).