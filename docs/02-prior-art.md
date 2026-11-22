# Prior art

Nobody has published Android Auto cluster navigation for a European Touareg. Two projects
each solve half of it.

## fifthBro/mh2p-cluster (Porsche, Audi experimental)

<https://github.com/fifthBro/mh2p-cluster>, site <https://fifthbro.github.io/>.
Audi fork with source and notes: <https://github.com/CrEaK/mh2p-cluster>
(read at commit `744bf6f8da`, 2026-10-18). Licence CC BY-NC-SA 4.0.

- **Status:** Porsche PCM5 stable, Audi experimental, **VW struck through**.
- **Tested firmware:** Porsche P2870 / P2874, Audi e-tron 55 on `MH2p_ER_AUG35_P2873`,
  a Q7 profile for P2711. Installer accepts brand codes PO and AU and firmware 26xx–28xx;
  `force_install.txt` overrides.
- **Turn arrows:** Java only. It subscribes Android Auto service attributes 7 (next turn
  event) and 8 (next turn distance) and feeds the cluster's navigation service. On
  Audi/Porsche this is done by shadowing `de.audi.*` classes (`AndroidAuto2Subsystem`,
  `CombiBAPServiceNavi`). **Those classes do not exist in the Touareg firmware.**
- **Live map:** a native library `gal_cluster.so` preloaded into the Android Auto process
  (`gal`). It adds a cluster display to the phone's service discovery, receives the H.264
  stream, and passes it to a small daemon that decodes it onto a cluster display slot.
  It intercepts 46 functions and relies on the memory layout of two internal objects,
  taken from the Porsche build. The project's notes say this part can only be tested on a
  car.
- **Known Audi limitation:** HUD arrows missing.

## plafp/touareg-cn-carplay-mods (Touareg, China, CarPlay)

<https://github.com/plafp/touareg-cn-carplay-mods> (read at commit `4fb7df80e8`,
2026-10-31), by a78c7 / plafp. GPL-3.0 for its own code.

- **Scope:** `MH2p_CN_VWG36_P2878` only, CarPlay only. Installer refuses any other train.
- **Same software family as the EU Touareg:** VW framework, same plugin mechanism, same
  data IDs (verified, see `03-verification.md`).
- **Turn data on cluster and HUD:** an HMI plugin (bundle 97, started last) writes the
  phone's manoeuvre into the stock navigation's own data:
  - 732 route guidance state
  - 738 current position
  - 741 distance to next manoeuvre, 744 turn-to street (through the `ASLNavBAPAPI`)
  - 751 and 838 manoeuvre lists (rows are `GenericCollector`: 0 main element, 1 direction,
    2 z-level, 3 side streets)

  The stock cluster service then sends them to the cluster and HUD.
- **"Navigation on mobile device is active":** suppressed by wrapping the CarPlay service
  object so `isCarPlayRouteGuidanceActive()` answers false.
- **Live map:** hooks the CarPlay process (`dio_manager`) through an `LD_PRELOAD` entry in
  `smartphone_integrator.json`, and renders into the spare "Google Earth cluster map"
  display slot layered over the factory cluster map (display list `[58, 33]`), so the
  factory map returns when the phone disconnects.
- **Careful installer:** backs up, verifies every edit, aborts without changing anything
  on a mismatch. The installer in this repository follows its structure.

## LawPaul/MH2p_SD_ModKit

<https://github.com/LawPaul/MH2p_SD_ModKit>, site
<https://lawpaul.github.io/MH2p_SD_ModKit_Site/>. CC BY-NC-SA 4.0, not for commercial use.
Used at commit `82f9452401` (2025-03-04).

A signed update package that runs shell scripts from `Mods/<name>/Update/install.sh`
(or `uninstall.sh` when `Mods/<name>/uninstall.txt` exists) as root in the update
environment. Lists the 2018–2024 Touareg as compatible. Its Touareg full-screen mod targets
exactly P2869.

After the mods it installs its own startup wrapper around `servicemgrmibhigh`. See
`07-how-to.md` for why and how this project skips that step.

## skydelis.eu

A commercial "Android Auto & CarPlay in Virtual Cockpit" product, remote via SD card. It is
for Škoda MIB2.5, not MH2p. Not applicable.

## What the Touareg EU needs

| Piece | Source |
|---|---|
| Write turn data into the VW cluster service | Chinese port (works as is on P2869, see verification) |
| Get turn data out of Android Auto | fifthBro's approach, re-implemented on the VW framework (Part A, done) |
| Get map video out of Android Auto | fifthBro's `gal` hook (Part B) |
| Draw map video on the cluster | Chinese port's renderer and display layering (Part B) |
