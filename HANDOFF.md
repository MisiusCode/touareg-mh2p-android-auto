# Handoff — Touareg CR MH2p (P2869), Android Auto in the cluster

A summary for another developer (for example the "Touarego" author on touareg-freunde.de, or
anyone working from fifthBro's / a78c7's projects) to check against their own work. It gathers
what this car is, what has been tried, what was measured, and what the owner can share.

Everything here is documentation and measurement. This repository does **not** contain the
Android Auto receiver hook, any firmware binary, or build/install material for the piece that
loads into the `gal` process — that part was not produced here. Pointers to the public source
for it are in `docs/02-prior-art.md`.

---

## 1. The car

| Item | Value |
|---|---|
| Model | VW Touareg CR, 2021 |
| Train | `MH2p_ER_VWG36_P2869` |
| Part number | 760035063B |
| Software | `H32.487.4_PREMIUM_EU`, image `CLU28_MMX2P_VW_ER_G36_029PROD` 2185.54.0 |
| Supplier | ALPINE, branch `rel/CLU28_newPDK`, build 23 Aug 2021 |
| HMI framework | VW `de.vw.mib.*` (NOT Audi `de.audi.*`), 6.37.7.SR5, J9 1.18, DSI 2_101 |
| OS | QNX; `/mnt/app` qnx6 ~1.1 GB |

Full detail: `docs/01-unit.md`.

**Important difference from Porsche/Audi:** the HMI is VW's own framework, so the Audi-targeted
cluster forks do not re-target by a string swap. See `docs/02-prior-art.md`.

## 2. The shop patch already on the unit (read this first)

The car was bought used with an unknown fullscreen CarPlay/AA mod already installed (installed
4 Mar 2026, right after a firmware update to P2869). What is on the unit:

- A 138-byte `servicemgrmibhigh` wrapper (original kept as `servicemgrmibhigh0`) that links
  `/mnt/ota/challenge.pub` → `/ifs/challenge.pub`.
- A modified `autorunner`.
- A fullscreen AA/CarPlay resource mod (`viewhandler.zip`).

`apps/gal` and `apps/dio_manager` are still the stock Aug-2021 files, so Android Auto itself was
not touched. Details and the exact bytes: `docs/01-unit.md`, section "The patch the car came with".

**Consequence for any installer:** packages that require exact stock files, or that install their
own startup wrapper / bundle 97, will refuse or will fight this patch. Touarego's installer and
the ModKit hook both fall in this group. This car needs an approach that leaves the existing
wrapper alone. All cards in this repo deliberately skip the ModKit startup hook for that reason.

## 3. What works today

Plugin `VWAndroidAutoNav` 0.7.1 (this repo, `plugin/`), an HMI add-on jar (bundle 97):

- Android Auto turn-by-turn **street name and distance** on the HUD, from the phone.
- Suppresses the "navigation on mobile device is active" message.
- Does **not** produce a turn arrow (see below).

It is installed through an SD card that skips the ModKit startup hook; see `plugin/installer/`.

## 4. What does NOT work, and why (the arrow)

Measured over several drives (`docs/05-test-log.md`):

- The phone's manoeuvre, written to the cluster navigation lists (751/838) and to
  `ASLNavBAPAPI` exactly as the factory navigation does, produces street + distance but no arrow.
- The HUD/cluster **arrow is a drawn picture**, not data. On this car:
  - `videoencoderservice.json` has one encoder block `HUD` (`/dev/mlb/isoTX1`) and one `CLUSTER`
    (`/dev/mlb/isoTX2`) — both are video streams the head unit encodes.
  - On the cluster display (id 4), the factory map is layer **33**. At every manoeuvre a layer
    **103** is stacked on top and removed afterwards (18 times in a 53-min factory-nav drive),
    in lockstep with the HUD KOMO view switching to "manoeuvre view" (type 1) / "route info"
    (type 2). So 103 is the manoeuvre picture.
  - The picture service (`DSIKOMOView`, instances 0 cluster / 1 HUD) answers only view-style
    changes; it ignores a manoeuvre offered to it while Android Auto (not factory nav) is guiding.
- With Android Auto guidance running, the owner confirms **no arrow on any screen** (HUD,
  cluster map view, cluster navigation view). Layer 103 never appeared for Android Auto.

Conclusion: an Android-Auto arrow needs a drawn picture onto the HUD/cluster display, i.e. the
same rendering path as the live map (Part B), not more list data. No one has phone arrows on a
European MH2p HUD yet.

## 5. The live-map plan and what was verified against this car

Full plan and stages (B0–B5): `docs/06-part-b-map.md`. In short, join two published halves:

- **Android Auto side:** fifthBro's receiver hook (phone → H.264 into a shared ring
  `/cluster_h264_shm`). Source is public — see `docs/02-prior-art.md`. *This half was not built
  or packaged in this repo.*
- **Renderer side:** the a78c7 / Touarego `cl_render` + `clusterd`, which read that ring and
  already run on P2869 for CarPlay.

Checked against this car's own files (from the dumps):

- `/usr/lib/libscreen.so.1` present; `apps/gal` links the screen + NvMedia + protobuf libraries
  its receiver side needs.
- `cl_render` (from Touarego) needs only `libscreen`, `libEGL`, `libGLESv2`, `libz`, `libm`, `libc`.
- `gal` is started by `smartphone_integrator` from `/run/enforcer/eso/bin/apps`, 2 setup + 2
  runtime retries. **Open question:** whether that sandbox accepts a replacement/wrapper for
  `gal`, or whether the `LD_PRELOAD` entry has to go in the `gal` child's `envs` in
  `smartphone_integrator.json` on `/dev/fs0p1` (what Touarego does for CarPlay).
- Cluster map is layer 33; a new map layer stacks above it the way 103 does.
- Free space: `/mnt/app` 943 MB free, `/mnt/ota` 8.1 GB free. Plenty.

## 6. What the owner can share

- The four read-only dumps (`dump-cards/` produced them) and the extracted `app/` tree, which
  include: `/etc/eso/production/*.json` (gal, smartphone_integrator, videoencoderservice,
  videoovermost), `/etc/config/*.conf` (display, dicluster, winmgr), partition file lists, and
  the stock `apps/gal`, `apps/dio_manager`, libraries.
- The plugin logs, including `vwaa_keep.log` with the full cluster-layer / KOMO-view recording
  of a factory-nav drive and an Android-Auto drive (`logs/2026-11-27_*`).
- `partb_info.txt`: on-car free space and file lists.
- Note that firmware binaries and the owner's private/licence partition are **not** in this
  public repo; they can be sent directly to a developer on request.

## 7. How to reach a working map (for whoever continues)

1. Confirm the `gal` load method on P2869 with the shop patch in place (sandbox vs. config
   `envs`), without disturbing the existing `servicemgrmibhigh` wrapper.
2. Build the receiver hook (fifthBro source) and the renderer (Touarego source) for QNX armv7.
3. Bring the phone stream into `/cluster_h264_shm`; render it as a layer above 33 on display 4.
4. Hand the cluster map slot back to the factory map on disconnect.
5. Test one change per SD card, each with a rollback card prepared first; never touch the boot
   chain. The car retries Android Auto only twice, so a bad test shows as "Android Auto won't
   start" until rollback.

See `docs/06-part-b-map.md` for the staged breakdown and failure modes.
