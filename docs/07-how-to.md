# How to

## Assemble a card

1. Format an SD card as FAT32 (32 GB or less is easiest on Windows).
2. From [LawPaul/MH2p_SD_ModKit](https://github.com/LawPaul/MH2p_SD_ModKit)
   (commit `82f9452401` was used) copy `Data/` and `Meta/` to the card root, unchanged.
   They are signed and checksummed; do not edit them.
3. Create `Logs/` in the root.
4. Create `Mods/<ModName>/Update/` and put `install.sh` and `uninstall.sh` there
   (LF line endings).

| Card | Mod folder | Files from |
|---|---|---|
| Dump 1 | `Mods/Touareg_Dump/Update/` | `dump-cards/card1/` |
| Dump 2 | `Mods/Touareg_Dump2/Update/` | `dump-cards/card2/` |
| Dump 3 | `Mods/Touareg_Dump3/Update/` | `dump-cards/card3/` |
| Dump 4 | `Mods/Touareg_Dump4/Update/` | `dump-cards/card4/` |
| Plugin | `Mods/VW_AA_ClusterNav/Update/` | `plugin/installer/*.sh` plus the jar from `plugin/dist/` renamed to `VWAndroidAutoNav.jar` |

Marker files go one level up, next to `Update/`: `uninstall.txt`, `full_dump.txt`
(card 1), `no_full.txt` (cards 3, 4).

## Run a card

1. Engine running and kept running. Phone unplugged.
2. Insert the card. Within about 10 s the unit reboots into the update and runs it.
3. Do not touch the unit. Remove the card when it asks.
4. Read `Logs/_ModKit_Update.log` and `Logs/<ModName>.log` on the card.

The update starts automatically on insertion, so never leave a card in while driving.

## Why every card deletes `/mnt/persist_new/servicemgrmibhigh.sh`

After running the mods, the ModKit wraps `/mnt/app/eso/bin/servicemgrmibhigh` with its own
script and sets up persistence under `/mnt/ota/modkit`. This unit already has a different
wrapper in that place (`01-unit.md`), and none of these cards needs the ModKit's hook.

`modkit_install.sh` checks for its temporary copy `/mnt/persist_new/servicemgrmibhigh.sh`
before that step and exits with `error: servicemgrmibhigh.sh not found` if it is missing.
The ModKit deletes that temporary file itself afterwards anyway. So each mod script
removes it as its last action. Result: the mod runs, the hook step is skipped, the unit's
startup wrapper stays as it was. Confirmed on the car on every run.

A mod that needs `Post/` or `Persist/` scripts cannot use this trick.

## The dump cards

| Card | Collects | Notes |
|---|---|---|
| 1 | Android Auto / CarPlay programs, HMI jars, `lsd.jxe`, libraries by name pattern, listings | Uses `print`, which errors on the unit; data is fine |
| 2 | The existing patch's files, small text files from `/mnt/ota` and `/mnt/persist_new`, update history, full listing of `/mnt/app` | Files over 2 MB skipped |
| 3 | Card 1's set plus config, more libraries, licence data into `Dump3/private`, full copy | Full copy fails (`tar` from the RAM disk); config is the update system's |
| 4 | Flash partitions `fs0p0..p4` mounted read-only one at a time; full copy with fallbacks | The one that works for both |

For a new unit, cards 2 and 4 together give everything. Only read-only mounts are used.

`Dump3/private` holds FEC licence files and `persistence.sqlite`. Keep it offline.

Files more than seven folders deep cannot be read through the Claude desktop bridge;
connect a deeper folder directly if needed.

## Convert the HMI image

```
git clone https://github.com/luka-dev/jxe2jar      (only src/ is needed)
python src/jxe2jar.py lsd.jxe lsd_p2869.jar
```

Pure Python 3, no packages. `lsd.jxe` (88 MB) becomes a 226 MB jar with 46,917 classes.
Read classes with `javap -cp lsd_p2869.jar -p -c <class>`; `-constants` shows constant
values.

## Build the plugin

```
export LSD_JAR=/path/to/lsd_p2869.jar
export OSGI_JAR=/path/to/jxe2jar/libs/org.osgi.framework-1.10.0.jar
export JDK8=/path/to/jdk8
plugin/build.sh
```

Requirements that matter:

- Class files must be version 48 (`-source 1.4 -target 1.4`), so a JDK 8 compiler.
  jxe2jar ships a Zulu 8 under `jvms/`.
- jxe2jar also carries J9 class libraries of other units under `libs/jcl/`. Compiling with
  one of those as boot class path would be a stricter check; not done so far.
- Use only `java.*` calls the unit's own code uses. The unit runs a reduced J9 library and
  the plugin is compiled against the JDK's. No `StringBuilder`, `String.format`, generics,
  enhanced for, autoboxing.
- The package name must not clash with stock classes; add-on jars load after `lsd.jxe`.

## Simulate on a PC

```
plugin/test/run.sh
```

Stand-ins for `ServiceManager`, `ListManager`, `GenericASLList`, `GenericCollector`,
`ASLNavBAPFactory`, `DSIProxyFactory` and `APIFactory` sit ahead of the real jar on the
class path; the real interfaces come from the converted image. The harness feeds a short
drive and prints every call the plugin makes to the "HMI". It takes about 80 s because of
the plugin's 45 s start delay.

It tests the plugin's logic. It cannot show what the cluster or HUD will draw.

## Collect the plugin's log from the car

Run the plugin's install card again, unchanged. It copies `/mnt/ota/system/logs/vwaa*` to
`Logs/vwaa_unit/` on the card before doing anything else. Wait half a minute after parking
first; the plugin flushes its log every 20 s.

## Recover

| Situation | Action |
|---|---|
| Plugin misbehaves, unit runs | USB stick with empty `vwaa_off.txt`; then uninstall |
| Uninstall | Empty `Mods/VW_AA_ClusterNav/uninstall.txt`, run the card |
| Plugin switched itself off | Re-run the card, or USB stick with `vwaa_reset.txt` |
| Plugin list damaged | The stock file is on the unit as `bundles.properties.vwaa_stock` and on the card under `backup/` |
| Unit does not reach the update at all | Service tools. Not solvable with a card |
| Everything back to factory | Official P2869 firmware package. Removes the full-screen mod and the startup patch too; Android Auto activation afterwards is unknown |
