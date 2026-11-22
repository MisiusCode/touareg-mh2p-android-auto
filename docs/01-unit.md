# The unit

Everything below was read from four read-only dumps of the car's own head unit
(2026-11-19 and 2026-11-21), unless marked otherwise.

## Identity

| Item | Value |
|---|---|
| Train | `MH2p_ER_VWG36_P2869` |
| MU version | 2869 |
| Part number | 760035063B |
| Software version | `H32.487.4_PREMIUM_EU` |
| Image version | `CLU28_MMX2P_VW_ER_G36_029PROD`, 2185.54.0 |
| Supplier / project | ALPINE, `project_mmx2p_vw_er_g36.cfg`, branch `rel/CLU28_newPDK` |
| HMI framework | 6.37.7.SR5 MIB2PMAIN, Java VM J9 1.18, DSI 2_101 |
| Firmware build date | 23 August 2021 (file dates) |
| OS | QNX; `/mnt/app` is qnx6, about 1.1 GB |

The interface software is **VW's own framework (`de.vw.mib.*`)**, not the Audi framework
(`de.audi.*`) that Porsche and Audi MH2p units share. This is why the Porsche/Audi cluster
mod cannot simply be re-targeted (see `02-prior-art.md`).

## Where things are

| Path on the unit | What |
|---|---|
| `/ifs/lsd.jxe` | The HMI as a J9 class image, 87,854,687 bytes |
| `/mnt/app/eso/hmi/lsd/lsd.sh` | HMI start script. Appends every `jars/*.jar` to the boot class path, runs with `-noverify` |
| `/mnt/app/eso/hmi/lsd/jars/` | Add-on jars. Stock content: `GEM.jar` |
| `/mnt/app/eso/hmi/lsd/config/bundles.properties` | HMI plugin (bundle) list, 13,156 bytes stock. Late-load line `Bundle.Startup.Lateload = 61,62,95`. Bundle numbers in use go up to 96 and 101; **97 is free** |
| `/mnt/app/eso/bin/apps/gal` | Android Auto receiver process, 1,472,868 bytes |
| `/mnt/app/eso/bin/apps/dio_manager` | CarPlay process, 958,480 bytes |
| `/mnt/app/eso/lib/libautoreceiver.so` | Android Auto receiver library, 1,079,444 bytes |
| `/mnt/app/eso/lib/libprotobuf3full.so` | Protobuf, used by the above |
| `/mnt/app/eso/bin/apps/dmdt` | Display manager debug tool (present) |
| `/dev/fs0p1` (system config) | `etc/eso/production/*.json`, including `smartphone_integrator.json` and `gal.json` |
| `/mnt/ota/system/logs/` | World-writable, empty in stock. Writable by the HMI at runtime (the plugin keeps its log here) |

### How Android Auto is started

`smartphone_integrator.json` on the system config partition defines the `gal` child process
with an explicit list of environment settings (`envs`) and the executable under
`/run/enforcer/eso/bin/apps`:

```
"gal": { "exec": "gal", "path": "/run/enforcer/eso/bin/apps",
         "envs": ["LD_LIBRARY_PATH=/eso/lib:...", "IPL_CONFIG_DIR_GAL=/etc/eso/production"], ... }
```

The `gal` entry has no `LD_PRELOAD`. Another child in the same file does use one in stock
(`LD_PRELOAD=/eso/lib/libsystemtime_hack.so`), so the mechanism is supported. A hook can be
added with one extra entry; the factory program does not have to be replaced.

### Displays

From `displaymanager.json` (the copy in the boot image; the system config partition has
none of its own, so this is very likely the one in use, but it was not read from a running
system):

| Display id | Role |
|---|---|
| 0 | Centre screen |
| 4 | Instrument cluster (LVDS2) |
| 5 | HUD (terminal "MOST") |

The HMI image contains the display slot names `DISPLAYABLE_KOMBI_MAP_VIEW` and
`DISPLAYABLE_GOOGLE_EARTH_KOMBI_MAP_VIEW`. The Chinese Touareg port gives them as
displayable 33 and 58; those numbers were not confirmed on this unit.

## Update environment

Inserting an update card reboots the unit into a separate update system that runs from a
RAM disk (`swupramdisk`). Consequences found the hard way:

- `/etc` there is the **update system's** `/etc`, not the running system's.
- The system config partition is not mounted. `/dev/fs0p1`, `p2` and `p3` mount read-only;
  `p0` and `p4` refuse.
- The shell is a ksh in which `print "--- text"` fails (`--: unknown option`). Use `echo`.
- `tar` exists but fails with `failed to return to initial working directory: Function not
  implemented` when started from the RAM disk. It works when the working directory is
  inside `/mnt/app`.
- Available: `cp`, `find`, `grep`, `awk`, `sed`, `mount`, `df`, `tar`. File dates written
  during an update are 1970/1980 because the clock is not set.

## Update history recorded on the unit

From `/mnt/persist_new/swup/history`:

| Date | Source | What |
|---|---|---|
| 2021-10-12 | OTA | Map update, Germany |
| 2021-12-25 | OTA | Map update, Germany |
| 2022-09-18 | OTA | Map update, Germany |
| 2023-08-27 | OTA | Map update, Sweden added |
| 2023-10-10 | OTA | Map update, Germany, Austria, Sweden |
| 2026-03-04 10:13–10:22 | removable media | Firmware to `MH2p_ER_VWG36_P2869` |
| 2026-03-04 10:32–10:36 | removable media | A package labelled `3G` (script type) |
| 2026-11-04 | customer update | Map update, Europe |
| 2026-11-19 onwards | SD card | The dump cards and plugin cards from this project (`MH2p SD ModKit v2 by LawPaul`) |

A firmware update rewrites the app partition, so nothing non-factory on it can be older
than 4 March 2026.

## The patch the car came with

The car was bought with a full-screen Android Auto / CarPlay mod already installed. The
dumps show a second, undocumented change installed in the same session.

**Non-factory files on the app partition (complete list):**

| File | Note |
|---|---|
| `/mnt/app/eso/bin/servicemgrmibhigh` | 138-byte shell script. The original binary is next to it as `servicemgrmibhigh0` (493,956 bytes) |
| `/mnt/app/eso/bin/autorunner` | 24,888 bytes, dated 28 Jan 2022. The stock `/usr/bin/autorunner` is 22,152 bytes |
| `/mnt/app/eso/hmi/lsd/Resources/skin1/viewhandler.zip` and `info.txt` | The full-screen mod's layout file and marker |
| `/mnt/app/armle/bin/openssl` | 421,992 bytes, dated 27 Mar 2019. Not identified as factory or added |
| `/mnt/app/sentinel.input`, `sentinel.input.sig` | Dated by an update run (clock unset). Probably written by the firmware update itself |

The wrapper script starts the original service manager and then creates two links at
every boot:

```
/mnt/ota/challenge.pub  ->  /ifs/challenge.pub
/eso/bin/autorunner     ->  /usr/bin/autorunner
```

`/mnt/ota/challenge.pub` (625 bytes, dated 5 Feb 2022) differs from the factory key in the
boot image. Root's `authorized_keys` is empty.

Reading: a service toolbox that swaps the unit's access key and its autorun program,
packaged in early 2022 and installed on 4 March 2026 by the `3G` package, ten minutes
after the firmware update. **The tool was not identified by name**, and its binary was not
analysed further.

Practical consequences:

- A stock ModKit card replaces `servicemgrmibhigh` with its own wrapper. On this unit that
  would overwrite the existing one. Every card in this project therefore stops the ModKit
  before that step (see `07-how-to.md`).
- A firmware update would remove both mods; whether Android Auto stays activated after one
  is unknown.
- The activation list `/mnt/persist_new/fec/ExceptionList.txt` exists; licence data is kept
  locally in `Dump3/private`, not here.
