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

## Arrow investigation

### 0.2 recording: factory navigation versus plugin (2026-11-21)

`logs/2026-11-21_plugin-0.2_factory-and-aa-recording.log`, lines `SNOOP other` (factory)
and `SNOOP plugin`.

- Factory, far from a right turn: list 751 = `28/192/0/""` (prepare turn, right).
- Factory, from about 250 m: `13/192/0/"À@"` (turn, right, side streets = characters with
  direction-byte values, here {192, 64}; other examples seen: `"Á@ "`, `"AÁÀ@"`). Bar on near
  the turn. Between manoeuvres 11, 10 and 6; two-row lists occur (`[11/0; 13/192]`).
- Factory state: rg=1, navStatus=0, kombi map 2/1. Lists 751 and 838 always equal.
- Plugin 0.2 received no Android Auto events in this run (phone connected after the unit
  had started; fixed in 0.3).

### 0.3 drive (2026-11-22): data identical, still no arrow

`logs/2026-11-22_plugin-0.3_aa-drive.log`. Events arrived, "resubscribed (guidance
started)" seen. The cluster service showed for Android Auto guidance exactly the factory
form: `rg=1 navStatus=0 kombiMap=2/1 bap=[28/192/0/""] turnTo="…" dist~700 bar=0` far and
`bap=[13/192/0/"À@"] … bar=1` near. Street and distance on the HUD, no arrow.

**Conclusion: the row format and the guidance state are not the cause.**

### Where the arrow comes from

Reading the P2869 HMI classes:

- The cluster protocol function `ManeuverDescriptor` (navsd, function 23) does send list 751.
  The cluster panel uses it; the HUD on this car does not draw from it.
- The HUD has a picture channel: `org.dsi.ifc.komoview.DSIKOMOView` ("KOMO view", instance 1
  = HUD, 0 = cluster), with view types 1 (manoeuvre view) and 2 (route info), plus a graphics
  stream `DSIKOMOGfxStreamSink`. The HMI (`mapstream.mosthud`: `StartupHudStreamSequence`,
  `HudMapView`, `HUDResyncSequence`, `ChangeDataRateSequence`) only sets style 5, enables
  the view, switches display context 4 and sets the stream rate. No HMI code fills the view;
  the picture is produced below the HMI by the factory navigation.

### 0.4 drive (2026-11-22): the picture service ignores Android Auto

`logs/2026-11-22_plugin-0.4_aa-drive.log`. Both view services connected. HUD reported
`enabled=true`, `visible=false`, `viewType=0` for the whole drive. 236 route info elements
sent; zero responses, no attribute change. Cluster instance reported nothing.

### Factory route with 0.4 listening (2026-11-22)

`logs/2026-11-22_plugin-0.4_factory-route-hud-picture.log`, phone unplugged:

| Time (ms) | HUD view |
|---|---|
| 63148 | viewType=2 (route info) — route started |
| 63471 | visible=true |
| 103754 … 108292 | viewType alternates 1 / 2 as a turn comes up (manoeuvre view) |
| 160654 | viewType=2 |
| 169620 / 170071 | viewType=0, visible=false — route stopped |

**Conclusion: the HUD arrow is the KOMO picture, and it is switched on and filled by the
factory navigation only.** Street and distance reach the HUD as data, the arrow does not.

### 0.5 drive (2026-11-26): the picture service takes no content

`logs/2026-11-26_plugin-0.5_probe-drive_tail-only.log`. No arrow seen. The log had passed
its size cap and restarted, so only the end of round 3 survived (fixed in 0.6).

- The HUD view answered the view-style commands only: 18 answers for 18 `setKomoViewStyle`
  calls. No answer or attribute change for `setRouteInfoElement`, `setRouteInfo`,
  `notifyVisibility` or the off/on step in the part of the log that survived.
- Setting the style back to 5 made the HUD view report `visible=true` then `false` within
  milliseconds, twice, with no content.
- HUD stayed `enabled=true visible=false type=0`; the cluster instance never reported.

**Conclusion: the HUD arrow cannot be fed through the existing services. Part A ends at
street name + distance.** Nobody else has phone arrows on the HUD of a European MH2p car
either (see `02-prior-art.md`, state of 2026-11-26).

The unit's `videoencoderservice.json` has one encoder block `HUD` (`/dev/mlb/isoTX1`) and
one `CLUSTER` (`/dev/mlb/isoTX2`): both pictures are video streams encoded by the head unit.
An arrow on the HUD would mean drawing onto the HUD display ourselves (after Part B).

### 0.6 / 0.7.1 (2026-11-26)

- **0.6**: experiment off by default (`vwaa_probe` marker runs it again); log in two files
  used in turn. Built, superseded by 0.7.1 before it was installed.
- **0.7.1**: 0.6 plus two read-only recorders for Part B: `DispEvents` logs
  `showDisplayablesResult` / `activeContext` per display (which layers the cluster shows),
  and the install card writes `Logs/partb_info.txt` (free space, file lists). Lines starting
  DISP / KOMO / GFX / PROBE / start / ACTIVATE also go to `vwaa_keep.log`, which is never
  wiped (stops at 400 KB). **Delivered, not yet run.** No dedicated drive needed; the next
  card collects the logs. To do once during Android Auto guidance: page the cluster between
  map and navigation view, press VIEW, note whether the navigation view shows an arrow.

### Event 14

Event 14 with side 0 appeared for "continue towards …" and is mapped to 11 / 0 (follow
street) since 0.3.
