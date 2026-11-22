# Test log

## Before any change (2026-11-21)

Photos with Google Maps guiding over Android Auto, stock software:

- Cluster, both views: **"Navigation on mobile device is active"** with a placeholder
  arrow. No turn data.
- Factory navigation views: map between the dials (classic), or full-width behind two
  side boxes (wide).

## Dump cards

| Card | Date | Result |
|---|---|---|
| 1 | 2026-11-19 | 23 files, 0 failed. `print` errors in the info file (harmless) |
| 2 | 2026-11-19 | 31 files, 0 failed, 3 skipped by size limit (`viewhandler.zip`) |
| 3 | 2026-11-21 | 305 files, 0 failed. Full copy failed (`tar`, empty `app.tar`). Config came from the update system's `/etc`, not the real one |
| 4 | 2026-11-21 | `fs0p1`, `p2`, `p3` read; `app.tar` 946,401,280 bytes |

On every run the ModKit log shows `error: servicemgrmibhigh.sh not found`, and the unit's
existing startup wrapper was unchanged afterwards. The hook skip works.

## Plugin 0.1, first run on the car (2026-11-21)

Install log: `logs/2026-11-21_install_plugin-0.1.log`.
Plugin log of two boots and one drive: `logs/2026-11-21_first-drive_plugin-0.1.log`
(timestamps are milliseconds since the unit's clock start).

**What worked**

- Unit booted normally with the plugin.
- Link check passed: property manager, nav API, lists 751 and 838, DSI proxy all present.
- Subscription to attributes 7 and 8 worked on the first try; events arrived.
- Wrapper installed around `AndroidAutoASLDataAdapter`; the "mobile device" message is gone.
- Self-disable armed and cleared; persistent log survived the reboot into update mode.
- **HUD shows the street name and the distance**, and the distance counts down correctly.

**What did not**

- **No turn arrow on the HUD**, at any turn. The factory navigation does draw arrows there.
- Cluster unchanged: factory map in both sizes, no turn information. (Expected for the
  map; whether one of the round side displays can show arrows was not checked.)
- Street line cropped when Google Maps sent "towards S…". Fixed in 0.2.

### Events Google Maps actually sent

| Event | side | angle | number | Road text example | Written (main/dir) |
|---|---|---|---|---|---|
| 1 depart | 0 | 0 | 0 | "towards Kriaušių gatvė", "Head east", "Klevų al." | 11 / 0 |
| 3 slight turn | 2 | 0 | 0 | "Birutės g." | 13 / 224 |
| 4 turn | 1 or 2 | 0 | 0 | "Obelų g.", "Turn right", "towards Konduktorių g." | 13 / 64 or 192 |
| 6 U-turn | 1 | 0 | 0 | "Make a U-turn", "Birutės g." | 25 / 64 |
| 7 | 2 | 0 | 0 | "A19 Kirtimų g." | 14 / 192 |
| 13 roundabout | 2 | 180, 270, 360 | 2, 3, 4 | "Klevų al.", "Dzūkų g." | 21 / 0, 64, 128 |
| 14 | 0 | 0 | 0 | "towards Slyvų g.", "Birutės g." | 15 / 0 |
| 19 destination | 2 | 0 | 0 | "Birutės g." | 11 / 0 |

Other observations:

- Distance updates come about every 10 s while moving, with a time-to-turn in seconds.
- Before each new instruction there is an event 1 with distance 0, a few hundred
  milliseconds ahead of the real one.
- The stock "route guidance active" flag went false for 10 to 15 s at a time during
  guidance, and stayed false for the last part of the drive while events kept coming.
- No `navFocus` notification was ever delivered to the plugin (it is an indication, not an
  attribute, and the plugin does not register for indications).
- No route-end event (0 / valid 2) was seen; the plugin deactivated on the 90 s timeout.

## The open problem: no arrow

Established:

- The plugin wrote the arrow rows (for example `{0: 13, 1: 192, 2: 0, 3: ""}`) to lists 751
  and 838 on every change and every 2 s.
- The firmware's cluster function reads exactly that row format from list 751
  (`03-verification.md`, section 5), and street and distance, written through the sibling
  API, do reach the HUD.
- The HUD can draw arrows (it does for the factory navigation).

Not known:

- Whether the arrow message is actually sent, and with what content.
- Whether the factory navigation's rows look different (side streets string, z-level,
  different main element numbers, more than one row).
- Whether the HUD on this car takes its arrow from somewhere other than list 751: list 838
  (the HMI's own manoeuvre view), or a condition such as the active guidance type
  (`ActiveRgType`, which depends on the cluster map status).

Ideas not yet tried, roughly in order of cost:

1. **Record the factory navigation** (0.2 does this): compare its rows in 751 and 838 and
   its state values with the plugin's.
2. Write a non-empty side streets string and/or copy whatever else the recording shows.
3. Check `ActiveRgType` / map switch state during factory guidance versus plugin guidance.
4. Try with the cluster's map view switched off, in case the arrow path depends on it.

### Doubtful mapping: event 14

In the Android Auto turn enumeration, 14 is "straight" (stated from memory, not
re-checked against a source). The plugin maps it to an exit arrow (15/16), following the
rule in the Porsche/Audi code for ramps. Google Maps sent 14 with side 0 for "towards
Slyvų g." and "Birutės g.", which looks like plain "continue straight". Probably should be
11 (follow street) with direction 0 when side is 0. Not changed yet.

## Plugin 0.2

Built 2026-11-21, simulated on a PC with the event pattern from the first drive.
**Not yet installed.** Planned recording drive:

1. Factory navigation only (phone unplugged), two or three turns with HUD arrows visible,
   then stop guidance.
2. Android Auto with Google Maps, two or three turns.
3. Park, wait half a minute, unplug the phone, run the card, read `Logs/vwaa_unit/vwaa.log`.

The lines to compare start with `SNOOP other` (factory) and `SNOOP plugin`.
