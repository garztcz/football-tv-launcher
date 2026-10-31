# Install guide

Everything is done over ADB (network debugging), the CleanYourTV way:
**back up first, disable never uninstall, and never disable the stock
launcher.** Tested on a TCL T6C (Google TV, Android 12); the helper notes
below are for firmware that behaves like TCL's.

## 0. Before you touch anything

```sh
adb connect <tv-ip>:5555
adb shell pm list packages -d > packages-disabled-before.txt
adb shell pm list packages    > packages-all.txt
adb shell settings list global > settings-global.txt   # and secure/system
```

Debloat lists per TV model live in the
[CleanYourTV examples](https://github.com/Gathin23/cleanyourtv/tree/main/examples).
Work from the list for your model, keep the packages it says to keep
(core Android/Google, TV/HDMI/tuner, remote/Bluetooth, keyboard, Play
Store/Services, firmware updater), and re-enable anything with
`adb shell pm enable <package>` if the TV misbehaves.

## 1. Install the apps

```sh
adb install football-tv-v1.apk     # from launcher/ build output
adb install tv-live-feed.apk            # optional, from live-feed/
```

Open the launcher once from the app drawer so it initializes.

## 2. Make it your home

First try the polite way: long-press the **Settings** pill →
**Make default Home**, and pick the launcher in the role dialog.

On some Google TV firmware (TCL confirmed) that is not enough: the
framework still starts the stock launcher on warm Home presses — the
stock launcher only forwards to the role holder once, at cold start. The
launcher therefore ships a **Home button helper** (an accessibility
service) that redirects to the custom home whenever the stock home
appears. Activate it like this:

1. Tell the launcher which package is your stock home. Write a
   `config.json` (see `config.example.json`) into its theme folder
   through its content provider:

   ```sh
   adb push config.json /data/local/tmp/config.json
   adb shell "content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json"
   ```

2. Grant the permission its self-repair needs, and enable the helper:

   ```sh
   adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
   adb shell settings put secure enabled_accessibility_services \
     "com.gathin.tvhome/com.gathin.tvhome.HomeKeyService"
   adb shell settings put secure accessibility_enabled 1
   ```

   (If other accessibility services are already enabled, append ours to
   the existing list instead of replacing it.)

3. **TCL only:** the firmware's TclAppBoot component refuses to bind
   services of apps without the auto-start op. Allow it (persists across
   reboots and reinstalls):

   ```sh
   adb shell cmd appops set --user 0 com.gathin.tvhome APP_AUTO_START allow
   ```

4. Reboot and press Home: you should land on the custom home. A short
   press redirects (the stock home may flash for a moment); a **long**
   press still opens the stock quick-settings panel, by design.

Firmware quirks this build already handles: the accessibility master
switch may reset to 0 at boot (the launcher's boot receiver repairs the
binding), and toggling the master switch alone does not re-bind a
service — rewriting the enabled-services list does.

> **Do NOT `pm disable` the stock launcher.** On TCL Google TV builds a
> system component (SetupWraith) guards it: Home resolution falls back to
> a recovery activity that loops re-enabling the stock launcher, and the
> Home key goes dead in the meantime. The helper exists precisely so you
> never need to disable it.

To undo the helper: Settings → Accessibility → "Home button" → Off, or
remove the service from `enabled_accessibility_services`.

## 3. Arm the live-feed add-on

Install it, then open **TV Live Feed** once and press **REFRESH NOW**.
That first open also arms the 6-hour job; a boot receiver re-arms it
after every reboot and refreshes once at boot. The status screen shows,
per theme, whether the last refresh worked. Until the first refresh
completes, the launcher shows its built-in snapshot.

## 4. Personalize

Everything optional lives in `config.json` in the launcher's theme
folder (same provider as above): `greeting_name` (shown after the
greeting; empty = no name), `columns`, `locale`, and more — the defaults
are in `launcher/app/src/main/res/values/config.xml`.

Switch club themes from the launcher: long-press **Settings** →
**Theme: …** cycles Persebaya → Como 1907 → Manchester United → Arsenal
→ Liverpool → Barcelona.

## 5. Roll back

```sh
adb uninstall com.gathin.tvhome            # stock home returns at once
adb uninstall com.gathin.tvhome.livefeed   # optional
adb shell pm enable <package>              # for anything you disabled
```

Nothing in this project ever uninstalls or disables a system app, so
removing the two apps above fully restores the stock experience.
