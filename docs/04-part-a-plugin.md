# Part A: the plugin (VWAndroidAutoNav)

Turn data from Android Auto to the cluster and HUD. No map, no native code, no hook into
any process. Source in `plugin/src/vwaa`, package `vwaa`, HMI bundle 97.

## What it changes on the unit

| Change | Path |
|---|---|
| New file | `/mnt/app/eso/hmi/lsd/jars/VWAndroidAutoNav.jar` |
| One line changed, three added | `/mnt/app/eso/hmi/lsd/config/bundles.properties` |
| Copy of the stock list | `.../config/bundles.properties.vwaa_stock` |
| Runtime files | `/mnt/ota/system/logs/vwaa.log`, `vwaa_boot` (self-disable counter) |

The edit to `bundles.properties`:

```
-Bundle.Startup.Lateload = 61,62,95
+Bundle.Startup.Lateload = 61,62,95,97
+
+# VWAndroidAutoNav: Android Auto turn-by-turn -> cluster/HUD (card mod VW_AA_ClusterNav)
+Bundle.Name.97 = VWAndroidAutoNav
+Bundle.Activator.97 = vwaa.Activator
```

Loaded last, so a failure to start cannot stop any stock bundle.

## Classes

| Class | Job |
|---|---|
| `Activator` | Bundle entry. Starts one daemon thread, never throws |
| `Bridge` | The loop: subscribe, decide whether guidance is live, write to the cluster service |
| `AaEvents` | Receives the Android Auto notifications and stores them. Touches nothing else |
| `Mapper` | Android Auto event to (main element, direction) |
| `Streets` | Tidies the text Android Auto sends as "road" (0.2) |
| `QuietAndroidAuto` | Wraps the cluster-side Android Auto service to hide the "mobile device" message |
| `Snoop` | Read-only recorder of what the cluster service sees (0.2) |
| `BootGuard` | Self-disable counter |
| `Flags` | Marker files |
| `Log` | RAM log plus a capped persistent copy |

## Flow

1. Bundle start, then wait 45 s for the HMI to settle.
2. `BootGuard.enter()`: read the counter; 3 or more means stay off. Otherwise write
   counter + 1.
3. Link check: touch each HMI class once (`ServiceManager.aslPropertyManager`,
   `ASLNavBAPFactory`, lists 751 and 838, `DSIProxyFactory`). Any failure leaves the plugin
   idle.
4. Every 300 ms:
   - subscribe to attributes 7 and 8 (retry every 10 s until it works);
   - install the wrapper around `APIFactory.androidAutoService` once that object exists;
   - take the latest event snapshot and decide `live`;
   - when live: set 732 = 1, write manoeuvre, street, distance; repeat all of it every 2 s
     because the factory navigation writes its own values to the same places;
   - when no longer live: clear lists 751/838, distance, street, set 732 = 0.
5. After 90 s of running, remove the self-disable counter.
6. Every 20 s, flush the log to `/mnt/ota/system/logs/vwaa.log` (capped at 150 kB).

### When is guidance "live"

`live = session && haveManeuver && !ended`

- `session`: the stock Android Auto service says route guidance is active, **or** a valid
  event arrived in the last 90 s. The stock flag drops to false for stretches during real
  guidance (seen in the first drive log), so it cannot be used alone.
- `haveManeuver`: a turn event with `valid == 1` has been seen.
- `ended`: set by `event == 0, valid == 2` (route cancelled or finished). If a destination
  event (19) came before, the arrival symbol is shown for 8 s first.

There is no explicit "guidance stopped" event in practice: the plugin deactivates about
90 s after the last event.

### Writes

| What | How |
|---|---|
| Guidance on/off | `ServiceManager.aslPropertyManager.valueChangedInteger(732, 1 or 0)` |
| Arrow | `GenericCollector` {0: main, 1: direction, 2: 0, 3: ""} via `ListManager.getGenericASLList(751).updateList(...)`, same to 838 |
| Street | `ASLNavBAPFactory.getNavBAPApi().updateBapTurnToInfo(street, "")` |
| Distance | `updateBapDistanceToNextManeuver(metres, 0, barOn, barPercent)`; bar counts up over the last 300 m |

Not written: time and distance to destination (Android Auto does not send them on this
channel), current position, lane guidance.

## Event translation

Parameters of `updateNavigationNextTurnEvent(road, side, event, angle, number, valid)`:
`side` 1 = left, 2 = right, 0 = unspecified; `valid` 1 = use it, 0 = placeholder,
2 with event 0 = route end.

Direction byte: 0 straight, 64 left, 128 back, 192 right (256 steps per turn,
counter-clockwise).

| Event | Meaning | Main element | Direction |
|---|---|---|---|
| 1, 2 | depart, name change | 11 follow street | 0 |
| 3 | slight turn | 13 turn | 32 left / 224 right |
| 4, 5 | turn, sharp turn | 13 turn | 64 left / 192 right |
| 6 | U-turn | 25 U-turn | 64 / 192 by side |
| 7 | on-ramp | 14 turn on main road | 64 / 192 |
| 8, 10 | off-ramp, merge | 16 exit left / 15 exit right by side; 11 when side is 0 | 0 |
| 9 | fork | 19 fork | 64 / 192 |
| 11, 12, 13 | roundabout enter, exit, enter-and-exit | 22 (clockwise, side 1) / 21 (counter-clockwise) | from angle, see below |
| 14 | see open questions | 16 when side is left, otherwise 15 | 0 |
| 19 | destination | 11, then 3 (arrived) on route end | 0 |

Roundabout direction, counter-clockwise traffic (side 2): `(angle + 180) mod 360`
converted to the byte. Checked against the first drive: exit 2 at angle 180 gives 0
(straight), exit 3 at 270 gives 64 (left), exit 4 at 360 gives 128 (back). Without an
angle the exit number is used: 1 right, 2 straight, 3 or more left.

When the non-roundabout event carries a non-zero angle it is used directly; Google Maps
sent angle 0 for every plain turn in the test.

These rules follow fifthBro's published table.

### 0.2 additions

- **Depart hold-back.** Google Maps sends event 1 with distance 0 immediately before every
  real instruction. In 0.1 this showed "follow street" for a fraction of a second. Now an
  event 1 or 2 during guidance is held for 1 s and dropped if another event follows.
- **Street text.** Leading "towards ", "toward ", "onto ", "on " removed. Texts that are
  instructions ("Turn right", "Head east", "Make a U-turn", "Continue…", "Keep…") become
  empty. English only; the phone's language decides these strings.

## The "mobile device" message

`QuietAndroidAuto` replaces `APIFactory.androidAutoService`. Its
`isAndroidAutoRouteGuidanceActive()` answers false while the plugin is subscribed and the
wrapper is installed (unless `vwaa_nohide` is present); everything else passes through.
On stop the original object is put back.

The cluster only re-asks when the stock service notifies it, so the message can linger
until the next change after the wrapper goes in.

## Switches

Empty files in `/tmp` or in the root of a USB stick / SD card (`/fs/usb0_0`, `/fs/usb1_0`,
`/fs/sda0`, `/fs/sdb0`), with or without `.txt`. Checked every 2 s.

| File | Effect |
|---|---|
| `vwaa_off` | Plugin passive: clears its data, passes the stock answers through |
| `vwaa_nohide` | Keep the stock "Navigation on mobile device is active" message |
| `vwaa_no838` | Do not write list 838 |
| `vwaa_nosnoop` | Recorder off |
| `vwaa_reset` | Clear the self-disable counter at the next start |

## Self-disable

Counter file `/mnt/ota/system/logs/vwaa_boot`. Written before the plugin touches any HMI
class, removed after 90 s of clean running. Three unclean starts in a row leave the plugin
off until the counter is removed (re-run the card, or `vwaa_reset`). Three very short
ignition cycles in a row would also trip it.

Confirmed on the car: armed and cleared on both boots of the first drive.

## Install card

`plugin/installer/` holds `install.sh`, `uninstall.sh`, `common.sh`. They go in
`Mods/VW_AA_ClusterNav/Update/` together with `VWAndroidAutoNav.jar`.

Install, in order:

1. Abort unless the train is `MH2p_ER_VWG36_P2869`, the card is writable and the files exist.
2. Copy any existing plugin logs from the unit to `Logs/vwaa_unit` on the card.
3. Establish the stock plugin list: the file on the unit if it is stock, else the on-unit
   backup, else our edits stripped out. Abort if the list is neither stock nor ours.
4. Build the new list in `/tmp` and check it three ways before touching the unit.
5. Save the stock list on the card (`backup/`) and on the unit (`.vwaa_stock`).
6. Copy the jar, verify its size on the unit. Abort before editing the list if that fails.
7. Replace the plugin list, verify; on failure restore the stock list and remove the jar.
8. Remove the self-disable counter.
9. Stop the ModKit before it installs its own startup wrapper.

Uninstall (`uninstall.txt` in the mod folder) restores the stock list byte for byte from
the on-unit backup, the card backup, or by stripping the edits, then removes the jar.

Running the install card again is safe and is how logs are collected.

## Known gaps

- Arrow not drawn on the HUD (the open problem).
- Event 14 mapping is doubtful (see `05-test-log.md`).
- Street tidying is English only.
- Factory guidance and Android Auto guidance at the same time overwrite each other.
- Waze and other apps untested.
