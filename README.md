# Touareg MH2p: Android Auto navigation on the cluster and HUD

Notes, tools and source from an attempt to get Android Auto navigation (Google Maps, Waze)
out of the centre screen and into the instrument cluster and head-up display of a
2021 VW Touareg CR.

**Car and unit:** Touareg CR 2021, Innovision Cockpit, MH2p head unit,
firmware `MH2p_ER_VWG36_P2869` (MU 2869, part number 760035063B, `H32.487.4_PREMIUM_EU`).

**Everything here is experimental.** It was written by reading the unit's own files and
two open-source projects, tested in simulation on a PC, and then tried on exactly one car.
A mistake can leave the head unit needing service tools. Do not reuse on another
firmware version without redoing the checks in `docs/03-verification.md`.

## Status (2026-11-27)

| Part | What | State |
|---|---|---|
| Dumps | Read-only SD cards that copy firmware files off the unit | Done, 4 cards, all ran on the car |
| A | Turn data (arrow, distance, street) from Android Auto to cluster and HUD | **Closed at street name + distance on the HUD.** 0.1 to 0.5 ran on the car; the HUD arrow is a picture stream that only the factory navigation fills, and 0.5 showed it cannot be fed through existing services. Current card: 0.7.1 (everyday version + read-only recorders for Part B) |
| B | The phone's live map in the cluster | Planned, B1 done, stopped at B2. Both halves now exist in public source (Android Auto hook: fifthBro; renderer on P2869: Touarego). Plan and stages in `docs/06-part-b-map.md` |

## Next steps

1. Plugin 0.7.1 is installed and is the everyday version (street name + distance on the HUD).
2. Part B is stopped at stage B2 from the assistant's side (see `docs/06-part-b-map.md`,
   "Status 2026-11-27"). To continue: take the recorded material to the Touarego /
   fifthBro projects, or build the Android Auto hook by hand from fifthBro's source.
3. Optional plugin-only idea, untested: ask the display manager about the HUD display's
   layers (the cluster shows the factory arrow picture as layer 103 above the map, 33).

Why there is no HUD arrow: `docs/05-test-log.md`, "Arrow investigation".

## Layout

| Path | Contents |
|---|---|
| `docs/01-unit.md` | The unit: firmware, partitions, displays, update history, the patch it came with |
| `docs/02-prior-art.md` | The projects this builds on and how each works |
| `docs/03-verification.md` | What was checked against the P2869 firmware, and how |
| `docs/04-part-a-plugin.md` | The plugin: design, event translation, install, switches |
| `docs/05-test-log.md` | On-car results, real Android Auto events, the open arrow problem |
| `docs/06-part-b-map.md` | Plan for the live map |
| `docs/07-how-to.md` | Making a card, running it, converting the HMI image, building the plugin |
| `dump-cards/` | Scripts of the four dump cards |
| `plugin/` | Plugin source, installer, PC test harness, built jars |
| `logs/` | Install log and plugin logs of the drives, one file per plugin version / test |

## Not in this repository, on purpose

- **Firmware files** from the dumps (`lsd.jxe`, `gal`, libraries, `app.tar`, the converted
  `lsd_p2869.jar`). They are VW's software. Keep them on a local disk.
- **`Dump3/private`**: licence (FEC) and settings data tied to the car.
- **The MH2p SD ModKit files** (`Data/`, `Meta/`). Get them from
  [LawPaul/MH2p_SD_ModKit](https://github.com/LawPaul/MH2p_SD_ModKit); the cards here used
  commit `82f9452401` (2025-03-04).

`logs/` contains street names from the test drives.

## Credits and licence

- **a78c7 / plafp**, [touareg-cn-carplay-mods](https://github.com/plafp/touareg-cn-carplay-mods)
  (GPL-3.0): how to feed the VW cluster service, plugin registration, installer structure.
- **fifthBro**, [mh2p-cluster](https://github.com/fifthBro/mh2p-cluster) and the Audi fork
  [CrEaK/mh2p-cluster](https://github.com/CrEaK/mh2p-cluster) (CC BY-NC-SA 4.0): Android Auto
  event meanings and the event-to-arrow rules; the map hook that Part B would use.
- **LawPaul**, [MH2p SD ModKit](https://github.com/LawPaul/MH2p_SD_ModKit) (CC BY-NC-SA 4.0):
  the SD card update mechanism.
- **luka-dev**, [jxe2jar](https://github.com/luka-dev/jxe2jar): converting the HMI image.

The plugin and installer are GPL-3.0 (`LICENSE`). Because they build on non-commercial
work, treat the whole thing as personal, non-commercial use only.
