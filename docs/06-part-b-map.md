# Part B: the live map (not started)

Goal: the phone's map (Google Maps, Waze) drawn in the cluster in place of the factory map.

## Pieces

| Piece | From | State |
|---|---|---|
| Hook in the Android Auto process that asks the phone for a second (cluster) video stream and receives it | fifthBro's `gal_cluster.so` | Exists as a released binary for Porsche/Audi; source in the Audi fork is older than the binary |
| Loading the hook | One extra `LD_PRELOAD` entry in the `gal` child's `envs` in `smartphone_integrator.json` on `/dev/fs0p1` | Mechanism confirmed in the dump; the Chinese port does the same for CarPlay |
| Decoder / renderer daemon that draws the stream on a cluster display slot | Chinese port (`cl_render`, `clusterd`), which derives from fifthBro's `cluster.c` | Tied to the CarPlay stream in the port; needs the Android Auto shared-memory input |
| Display layering so the factory map returns on disconnect | Chinese port's `Rebind`: display 4 shows `[58, 33]` | Slot names exist in P2869; numbers unconfirmed |
| Starting the daemon at boot | Needs something that runs at boot | **Problem on this unit**, see below |

## Risks

1. **Object layouts.** The hook assumes the layout of `MessageRouter`'s endpoint table and
   of `shared_ptr<IoBuffer>` from the Porsche build. P2869 is in the same build window
   (Porsche 2870/2874, Audi 2873) and all 46 function names are present, but the layouts
   were not compared. A mismatch can crash Android Auto on every connection until the
   `LD_PRELOAD` entry is removed.
2. **Service ids.** The hook adds services 14 and 21 to the phone's service discovery. If
   the VW receiver already uses those ids the phone rejects the session and `gal` restarts
   in a loop (per the Audi fork's notes).
3. **Boot-time start.** Both source projects start their daemon from the ModKit's startup
   hook. On this unit that hook would replace the existing, unidentified startup wrapper
   (`01-unit.md`). Options: start the daemon from the Java plugin, or extend the existing
   wrapper. Neither is designed yet.
4. **Recovery.** Removing the hook means restoring one text file on the system config
   partition from an update card. That needs the update to start, which it does as long as
   the HMI runs; Android Auto crashing does not prevent that.
5. **No bench unit.** Every attempt is a first test on the car.

## Suggested order, if this is ever done

1. Get the hook's exact expectations from the newest source or from fifthBro directly
   (the Discord linked on his site). Ask whether a VWG36 build exists.
2. Compare the two object layouts in the dumped `libautoreceiver.so` against the Porsche
   one. Without a Porsche binary this needs the developers' help.
3. First on-car step: the hook with its diagnostic switch `GAL_CLUSTER_SD_LOG=1`, which
   dumps the stock service list to `/tmp/aa_sdresp_*.bin`, and no renderer. That answers
   the service-id question (risk 2) before anything is drawn.
4. Then the renderer on the spare slot, then the layering.

The safe way to do any of this is a second head unit on a bench with a video grabber.
