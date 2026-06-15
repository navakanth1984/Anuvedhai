# AnuVedhai React Native Wrapper 📱

This directory contains a **production-ready React Native cross-platform project (Expo compatible)** designed to wrap-around and distribute AnuVedhai's web logic across native **Android (Google Play)** and **iOS (Apple App Store)** environments. 

By leveraging React Native's high-fidelity `WebView` component integrated with native layout controls and sensory engines, you can compile and publish high-performance mobile packages with local device capabilities.

---

## 🏗️ Architectural Core Features

1. **Native Shell Controls (`App.tsx`)**: Integrates high-contrast top command buttons to quickly reload, monitor URLs, verify network states, and invoke system resources.
2. **Dynamic Back Press Logic (Android)**: Captures hardware back presses on Android. Instead of shutting down the application when tapping back inside a child layout, it seamlessly flows backward through the translated web page history.
3. **Resilient Network Decoupler**: Polling interface listens via `expo-network`. Transmits immediate user warning cards when mobile connectivity is interrupted, with an action to retry.
4. **Interactive postMessage Bridge (`BridgeHandler.ts`)**: Allows the web logic to dispatch custom JSON payloads that evoke native system behaviors including:
   - High-fidelity haptic feedback patterns (Tactile sensory indicators upon translation).
   - System Native Share dialog triggers to export translations over WhatsApp, Telegram, or email.
   - Device alert notifications.
5. **Hands-Free Voice Command Mode (NLP Parser & TTS)**: Integrates an overlay controller allowing users to vocally control and navigate translator workspaces. Driven by a central Natural Language Processing (NLP) pattern matcher, speech inputs trigger interface reloads, webview tab navigations, data cache cleanups, and high-performance vocal transcript readbacks using the `expo-speech` synthesizer.

---

## 📁 Directory Structure Overview

```text
/react-native-wrapper/
├── App.tsx                    # Main app configuration, loading gates, and state handlers
├── app.json                   # Expo build parameters, bundle identifiers, and hardware permissions
├── package.json               # Node packages (React, React Native, WebView, Network, Haptics, Speech)
├── tsconfig.json              # TypeScript compilation specifications
├── ReactNative_ReadMe.md      # This detailed guide
└── src/
    ├── components/
    │   └── NativeHeader.tsx   # Top control header including Refresh & Clipboard copy actions
    └── utils/
         ├── BridgeHandler.ts   # Event bridge parsing web-to-native triggers
         ├── OfflineCache.ts    # Async-storage persistence manager for translation transcripts
         └── VoiceCommandNLP.ts # Natural Language Processing keyword matcher and voice-response pipeline
```

---

## 🚀 Setting Up the Development Workspace

Before running, ensure you have **Node.js (v18+)** and **npm/yarn** installed locally on your machine.

### 1. Install Dependencies
Navigate into the wrapper directory and install the necessary npm dependencies:
```bash
cd react-native-wrapper
npm install
```

### 2. Launch Development Server
Launch the Expo bundler:
```bash
npm run start
```
This boots up the **Expo DevTools** directly in your terminal, displaying a unique QR code.

### 3. Running on Devices
* **Android**: Install the **Expo Go** application from the Google Play Store, and scan the terminal QR code.
* **iOS**: Install the **Expo Go** application from the Apple App Store, open your system camera, and scan the QR code to run live on your iPhone.

---

## ⚡ Building Production Distribution Packages

Expo uses the EAS (Expo Application Services) platform to build store-ready binaries directly in the cloud, removing complex local OS setups for Android Studio or Xcode.

### Log in to Expo (One-time)
If you don't have an Expo account, create one at [expo.dev](https://expo.dev) and run:
```bash
npm install -g eas-cli
eas login
```

### Initialize Project Configuration
```bash
eas project:init
```

---

### 📦 Distributing to Android (Google Play / `.aab` / `.apk`)

To generate an optimized `.apk` (for manual installations) or `.aab` (for submitting to the Google Play Store):

1. **Configure credentials**: Run the configuration wizard which automatically sets up secure Android keystores:
   ```bash
   eas build:configure
   ```
2. **Build and compile**:
   ```bash
   # Build a quick APK for testing
   eas build --platform android --profile preview

   # Build a release AAB for Play Store listing
   eas build --platform android --profile production
   ```

---

### 📦 Distributing to iOS (App Store / `.ipa`)

To compile and build an `.ipa` package for Apple App Store distribution:

1. **Configure credentials**:
   ```bash
   eas build:configure
   ```
2. **Build and compile**:
   ```bash
   # Build a profile-signed binary to test on registered devices
   eas build --platform ios --profile preview

   # Compile optimized production package to upload to TestFlight / App Store
   eas build --platform ios --profile production
   ```

---

## 🔗 Bidirectional Web-to-Native Bridge

To invoke native capabilities from your centralized web deployment, dispatch a `postMessage` protocol packet:

```javascript
// Web Side Dispatcher
const triggerNativeHaptic = () => {
  if (window.ReactNativeWebView) {
    window.ReactNativeWebView.postMessage(JSON.stringify({
      type: 'TRANSLATION_SUCCESS',
      payload: {}
    }));
  }
};

const shareTranslationText = (translatedText) => {
  if (window.ReactNativeWebView) {
    window.ReactNativeWebView.postMessage(JSON.stringify({
      type: 'SHARE_TRANSLATION',
      payload: { text: translatedText }
    }));
  }
};
```
These packages are automatically intercepted by on-device observers and passed directly to `/src/utils/BridgeHandler.ts` to trigger tactile vibrations or invoke default system worksheets!

---

## 🎙️ Hands-Free Voice Commands Reference Guide

The hands-free controller uses modern match mappings to translate spoken phrases into app activities. Below is the list of available voice triggers, their mapping IDs, and physical behaviors:

| Spoken Phrase Trigger | Action Performed | Voice Synthesis Response (TTS Output) |
|---|---|---|
| *"go to dialogue mode"*, *"open chat"* | Swaps Web App focus to Dialogue Translation | *"Navigating to dialogue translation workspace."* |
| *"go to call translator"*, *"phone mode"* | Swaps Web App focus to Call Integration VoIP tab | *"Opening cellular and video call translator integration hub."* |
| *"go to mascot buddy"* | Swaps Web App focus to Virtual Mascot chat assistant | *"Switching to your virtual mascot translator companion."* |
| *"play recent translation"*, *"speak recent"* | Retrieves latest cached text transcript from local database and reads content out | Reads back: *"Recent translation: [Translated text content]"* |
| *"refresh web app"*, *"restart translator"* | Reloads WebView container page instantly | *"Refreshing live translation workspace channels."* |
| *"clear offline cache"*, *"delete cache"* | Wipes clean all Async-Storage transcript backups | *"Wiped all localized backup transcripts from offline memory."* |
| *"copy link"*, *"get address"* | Saves active translation room link to clipboard | *"Workspace link copied successfully to system clipboard."* |
| *"say help hands free"*, *"voice menu"* | Vocalizes complete summary of available triggers | *"Hands free voice commands enabled. You can say..."* |

Users can access this hands-free menu instantly by tapping the floating **🎙️ Voice Ctrl** button at the bottom-right corner of the mobile device. This pops up the interactive oscilloscope-styled soundwave analyzer and active command logs terminal!

---

## ↩️ Dynamic 'Cancel Last Command' (Undo) Mechanics

The AnuVedhai mobile wrapper is designed with resilient safety mechanisms to protect user states against accidental voice execution. When commands are dispatched via the voice interface, the container tracks the transaction signature to allow seamless rollbacks.

### 🛡️ Protected Operations & Rollback Behaviors:
1. **Accidental History/Cache Clearing (`clear_offline`)**: 
   - **Risk**: User accidentally commands the system to wipe their offline translations. 
   - **Protection**: Before executing the disk-format action, the system automatically takes an immutable memory snapshot of the cached translations list.
   - **Undo Action**: Clicking **↩️ UNDO LAST** or command reverting restores the entire cached array directly back into `AsyncStorage` and updates the active list instantly, accompanied by vocal audio confirmation.
2. **Accidental Tab/Workspace Navigation (`switch_` routes)**:
   - **Risk**: User switches screens unexpectedly in the middle of writing or typing a document.
   - **Protection**: The app tracks the previously active tab before switching.
   - **Undo Action**: Reverts the client's current screen focus back to the original layout automatically.

This can be triggered dynamically inside the voice panel overlay using the **↩️ UNDO LAST** button in the header bar or the descriptive **↩️ Cancel / Revert Last Action** prompter box that pops up contextually when a command has been recorded!

---

## 🎚️ Adaptive 'Microphone Sensitivity' Noise Gate

To support pristine operation in ambient urban noise, coffee shops, or silent conference halls, a high-fidelity **Microphone Sensitivity Limit Gating Slider** has been added to the control pane.

### 🎛️ Operational Parameters & Audio Thresholds:
- **Range**: `-60 dB` (extremely sensitive) to `-10 dB` (highly selective/gated).
- **Responsive Oscilloscope Feedback**: The voice command soundwave oscilloscope displays a dynamic magenta-colored **Gate Limit Cutoff Line** corresponding to your decibel setting.
- **Noise Filtering Indication**:
  - Soundwaves that fall *below* your cutoff decibel level are dynamically greyed out/gated, showing that ambient hums are actively filtered from the NLP parser.
  - Sound waves that rise *above* the cutoff line illuminate in neon orange, signifying active speech registration.

### ⚙️ Recommended Settings:
1. **`-60 dB` to `-50 dB` (Low Noise Floor Limit)**: Suitable for silent private workspaces or bedrooms. Captures soft whispered speech accurately.
2. **`-45 dB` to `-30 dB` (Balanced Noise Floor Limit)**: Perfect for normal speaking volumes in office environments or standard rooms. Filters low-frequency background hums.
3. **`-25 dB` to `-10 dB` (High Noise Floor Limit)**: Filters out typing keyboards, distant chatter, wind interference, or coffee shop traffic. The user must speak clearly and closely to trigger navigation.

---

## 📋 Session Audits & 'Recent Voice Commands' Logger

To maximize session situational transparency, the overlay integrates a continuous, scrollable **Recent Voice Commands Log** tracking up to the last 10 executed vocal requests.

### 🔍 Metadata Recorded per Transaction:
- **Session Timestamp**: Captures the exact clock signature (`HH:MM:SS`) when the NLP parser triggers.
- **Vocalized Phrase**: Highlights the literal dictated text command entered/simulated.
- **Matched Action**: Deciphers the target operational routing executed.
- **Real-Time Execution Status**:
  - `EXECUTED` (Green): Command successfully matched and actioned on the web client.
  - `REVERTED` (Orange ↩️): The command was cancelled via the Undo/Rollback prompt.
  - `MISSED` (Grey ❓): Phrase did not match active navigation patterns.
  - `ERROR` (Red ❌): Internal exception or network failure interrupted parser execution.

This is loaded dynamically in a scrollable, nested window at the bottom of the interface panel so operators can inspect voice histories instantaneously.

---

## 🗣️ Adaptive 'Set Wake Word' & Audio Calibration Engine

To enable complete hands-free communication, a background **Wake Word Detector** has been implemented. This replaces standard manual-only touch buttons, listening for a custom vocally trained keyword to trigger the overlay hands-free.

### ⚙️ Customizable Operational Parameters:
- **Automatic Background Radar**: A green pulsing LED indicating active audio detection of your wake word, or custom gate limits when disabled.
- **Interactive Calibration Recorder**:
  - Type the desired wake word trigger (e.g. `Scribe`, `Translate`, `Anu`).
  - Press **🎙️ Record Word** inside the Voice Settings panel.
  - An audio calibration countdown will trigger (`3s`, `2s`, `1s`), measuring background noise and training speech signature.
  - Instant voice synthetic feedback confirms: *"Calibration verified. Wake word trigger updated to [your word]"*.
- **Quick Vocal Hotkeys**: Swift-click predefined preset tags (`scribe`, `translate`, `hey scribe`, `buddy`) to hot-swap active wake triggers instantly.
- **Hands-Free Simulator Bar**:
  - Located on the primary closed screen, allowing operators to type or dictate the custom wake word to trigger the Scribe controller hands-free without touching the screen.
  - Seamless manual voice override acts as a secure bypass at any time.

---

## 🔒 SecureStore Transcript Auto-Save & Crash Recovery

To prevent translation and dictation history loss during unexpected system shutdowns or mobile app crashes, the React Native container is configured with a robust **SecureStore Auto-Save Engine**.

### ⚙️ Auto-Save Features:
- **Periodic Background Sync**: Runs silently every 30 seconds to capture the exact estado (sequence) of active translation transcripts in local memory.
- **Hardware-level Encrypted Storage**: Leverages iOS/Android hardware Keychain/Keystore via `expo-secure-store` to encrypt cached transcripts securely, avoiding raw data vulnerability.
- **Visual Status Transparency**: A green glowing status badge is rendered on the control overlay dashboard (`✓ Auto-Saved HH:MM:SS`), informing the operator of successful backup status.
- **Self-Healing Startup Recovery**:
  - Upon fresh startup, the container inspects SecureStore for any uncommitted transcripts from previously interrupted sessions.
  - If a crash signature is detected (transcripts preserved in SecureStore but not loaded in standard offline AsyncStorage cache), the engine automatically merges them.
  - Generates a Native dialog notification: *"Session Recovered 🛡️ - An unexpected app exit was detected. We have successfully restored translation transcripts to your local history cache!"*
  - Safely overwrites and re-syncs state lists. Cleans records upon manual master flush actions to conform with user privacy.

---

## ⚡ Battery Conservation & Auto Eco Saver Mode

To prolong operational field-times and conserve handset resource bounds, an intelligent, dynamic **Battery Conservation & Auto Eco-Saving Co-Processor** is fully integrated. 

### ⚙️ Battery Conservation Features:
- **Intelligent Resource Auto-Throttling**: Continuously monitors the device battery percentage. Once charge registers **below 20%**, the system automatically triggers a power-preservation sequence.
- **Adaptive Frame-Rate Deceleration**: Soundwave graphics capture frequency automatically dampens, reducing the animation cycle speed by 4x (switching from `100ms / 10Hz` down to `400ms / 2.5Hz`). This limits layout render-cycles and minimizes CPU draw.
- **Auto-Save I/O Mitigation**: Minimizes recurring storage operations. Increases SecureStore encrypt/decrypt write parameters by 4x, expanding the automatic transcript backup interval from `30 seconds` to `120 seconds`.
- **Haptic & Audio Tuning**: Automatically adjusts TTS synthesis cadence and limits haptic motor depth to safeguard residual milliwatts.
- **Hardware Integration & Sim Panel**:
  - Leverages genuine iOS & Android battery level sensor queues via the `expo-battery` SDK.
  - Multi-state tactile control chips are nested on the dashboard, allowing developers/users to preview different charge situations (`100%`, `50%`, `15%`, `5%`) to test high-efficiency parameters instantly.
  - Generates custom user alerts and glowing green status badges indicating exact power/frequency limits.

---

## ✈️ 36-Hour Traveller Language Accelerator

For operators planning trips or interacting with native speakers, the container integrates an interactive **36-Hour Crash Course & Vocabulary Trainer** right on the hands-free dashboard overlay.

### ⚙️ Interactive Accelerator Features:
- **Core Languages catalog**: Built-in high-fidelity courses for **Hindi (हिन्दी)**, **Spanish (Español)**, **French (Français)**, and **Japanese (日本語)**.
- **Respective Native script rendering**: Each language item lists its native alphabetic characters alongside its English counterpart for pronunciation confidence.
- **Zero-latency Accent Speech Synthesis**: Includes a dedicated `🔊 Play Name` audio trigger that synthesis-reads the native vocabulary utilizing native iOS/Android speech vocal organs with high-precision local accents.
- **36-Hour Timeline Blueprint**: Dynamic schedules showing study guidelines for Days 1, 2, and 3 corresponding to: Essentials, transit and hotels layout navigation, custom menus, and emergency signaling.
- **Multi-Category Vocabulary Filter**: Instant category badges allow filtering of custom words:
  - `All` - entire syllabus.
  - `Essentials` - greeting etiquette and politeness formulas.
  - `Transit & Hotels` - station location, fare negotiation, check-ins.
  - `Dining & Social` - accepting receipts, ordering pure vegetarian styles.
  - `Emergency` - summoning ambulances and doctors.
- **Interactive Match Practice Quiz**:
  - Automatically loads and randomizes questions selected from your active language locale and filtering configurations.
  - Presents English phrases and prompts operators to match and select the correct native translation from multiple high-fidelity choice distractors.
  - Evaluates user input live, triggering native success/error haptic feedback cues.
  - Seamlessly tracks session score rates (`Correct Match Matches / Total Attempts`) with a built-in scoreboard reset.
  - Displays context explanations post-selection, exposing exact spelling formats, phonetic romanizations, and dedicated play accent audio synthesis buttons to practice pronunciation.


