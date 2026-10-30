# Installing ChamBoard

> **Test version.** There's no app-store release yet. Get a test build from GitHub, or build it on your computer (see below).

## Getting a test build

**From GitHub:**

1. Open the project's **Actions** page on GitHub, choose **CI**, and open the latest green run on `main`.
2. Under **Artifacts**, download **chamboard-debug-apk** and unzip it.
3. Copy the `.apk` file to your Android phone (Android 10 or newer) and open it. Android will ask you to allow installing apps from that source.

**On your computer:** with Android Studio (or the Android SDK) installed, run `./gradlew :app:assembleDebug` in the project folder. The app file appears in `app/build/outputs/apk/debug/`. Copy it to your phone as in step 3.

## Turning it on

1. Open the **ChamBoard** app. It walks you through the next steps.
2. Tap **Open keyboard settings** and switch on **ChamBoard**. Android warns that a keyboard can see what you type. That's true of every keyboard. ChamBoard has no internet access, so it can't send anything anywhere.
3. Tap **Switch keyboard** and choose ChamBoard. Later, you can switch with the keyboard icon that appears while typing.

## Using it

- Type as normal. Your words appear in the keyboard's **private draft** strip, not in the app.
- In Full Cloak you'll also see a **"Sends:"** line: what the app will actually receive, with rewritten words highlighted.
- Tap **EMIT** (or **GO** in search boxes) to send. The keyboard types it in for you. Tap **STOP** at any time; whatever hasn't been sent goes back into your draft.
- The strip above the draft:
  - **Mode.** Tap to change how much protection this box gets; hold for no protection in this box only.
  - **Mask.** Tap to switch between this app's own mask and your named masks; hold for a fresh mask.
  - **Style.** Tap to change how strongly your writing is tidied.
  - **Paste / Copy.** Tap to use ChamBoard's private clipboard; hold to use the phone's clipboard.
  - **✕** erases the draft.
