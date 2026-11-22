# What was verified against P2869

All checks were made off the car, on files dumped from it. "Converted image" means
`/ifs/lsd.jxe` turned into a normal jar with `jxe2jar` (46,917 classes) and read with
`javap`.

## 1. Data IDs: identical to the Chinese firmware

The Chinese port hard-codes six IDs from P2878. In P2869 they have the same numbers and
these names (from the generated constants classes):

| ID | Constant in P2869 | Class (under `generated.de.vw.mib.asl.internal.`) |
|---|---|---|
| 732 | `ROUTE_GUIDANCE_STATE` | `navigation.bap.ASLNavigationBapPropertyManager` |
| 738 | `CURRENT_POSITION_INFO` | `navigation.bap.ASLNavigationBapPropertyManager` |
| 741 | `DISTANCE_TO_NEXT_MANEUVER` | `navigation.bap.ASLNavigationBapListConstants` |
| 744 | `TURN_TO_INFO` | `navigation.bap.ASLNavigationBapListConstants` |
| 751 | `MANEUVER_DESCRIPTOR` | `navigation.bap.ASLNavigationBapListConstants` |
| 838 | `MANEUVER_DESCRIPTOR` | `navigation.map.maneuverview.ASLNavigationMapManeuverViewListConstants` |

`NavigationASLDataAdapter` (cluster protocol side) reads 732, 738, 741, 744 in the
matching getters, and the manoeuvre iterator reads list 751.

## 2. The port's plugin compiles against P2869

All of `src/hud-java/vwcn/*.java` compiled with JDK 8, `-source 1.4 -target 1.4`, against
the converted image and an OSGi jar: no errors. So every class, method and field it uses
exists with a compatible signature. Fields it reaches by name also exist:
`APIFactory.carPlayService`, `APIFactory.navigationService`, `CarBCSpeed.speedValue`.

Limit of this check: it used the JDK's class library, not the unit's reduced J9 library.

## 3. Android Auto turn events

`org.dsi.ifc.androidauto2.DSIAndroidAuto2Listener` in P2869:

```
updateNavigationNextTurnEvent(String road, int turnSide, int event, int angle, int number, int valid)
updateNavigationNextTurnDistance(int distanceMetres, int timeSeconds, int valid)
navFocusRequestNotification(int type, int valid)
```

`DSIAndroidAuto2` attribute 7 is the next turn event, 8 the next turn distance.
The stock handler (`TargetAndroidAuto2DSI`) receives both and only writes a trace line;
the factory software does nothing with them.

Subscribing from a plugin works the way the port subscribes to other services:

```java
DSIProxy p = DSIProxyFactory.getDSIProxyAPI().getDSIProxy();
DSIAndroidAuto2 aa = (DSIAndroidAuto2) p.getService(null, DSIAndroidAuto2.class);
aa.setNotification(new int[] { 7, 8 }, listener);
```

Confirmed on the car on 2026-11-21: events arrive (see `05-test-log.md`).

## 4. The "Navigation on mobile device is active" message

`de.vw.mib.bap.mqbab2.navsd.functions.InfoStates` reports state 6 when
`CarPlayService.isCarPlayRouteGuidanceActive()` or
`AndroidAutoService.isAndroidAutoRouteGuidanceActive()` is true. `InfoStates` is the only
caller of the Android Auto method. `AndroidAutoService` is a three-method interface held in
the private static field `APIFactory.androidAutoService`
(stock object: `AndroidAutoASLDataAdapter`). Wrapping it is therefore narrow in effect.

## 5. How the cluster service builds the arrow message

`navsd.functions.ManeuverDescriptor` (function id 23):

- Sends only if `getRouteGuidanceState() != 0`.
- Reads up to three rows from list 751 through
  `NavigationBapManeuverDescriptorTransformer`, which casts each row to `GenericCollector`
  and reads column 0 (main element), 1 (direction), 2 (z-level), 3 (side streets).
- `mapToBAPMainElement`: internal 32 becomes 15, 33 becomes 16, everything else passes
  through.
- `validateManeuverData` clears z-level and/or side streets for some element types; it
  does not drop the arrow.

`navsd.functions.RGStatus` (id 17) reports guidance active when navigation status (733)
is 0 and state 732 is non-zero. `ActiveRgType` (id 39) depends on the map switch state and
the cluster map status.

So on paper the rows the plugin writes are in the right form. On the car the HUD still
draws no arrow; see `05-test-log.md`.

## 6. Plugin registration

`bundles.properties` on the unit has the same late-load line as the Chinese stock file
(`Bundle.Startup.Lateload = 61,62,95`) and bundle 97 is unused. `lsd.sh` adds
`jars/*.jar` to the boot class path unconditionally, after `lsd.jxe`, so an add-on jar
cannot shadow stock classes.

## 7. The Android Auto map hook (Part B) by name

The hook source intercepts 46 functions:

- 38 are exported by the unit's `libautoreceiver.so`.
- 3 (`CodedOutputStream::WriteRaw`, `MessageLite::SerializeToArray`,
  `MessageLite::ParseFromArray`) are in `libprotobuf3full.so`.
- 5 are older-version names (`ByteSize()`), for which the hook has `ByteSizeLong`
  fallbacks.

Name presence says nothing about the two object layouts the hook assumes
(`MessageRouter` endpoint table, `shared_ptr<IoBuffer>`). Those are unverified.

## 8. Basic Java calls

Every `java.*` method the plugin uses is one the unit's own HMI code or the Chinese port's
released jar also uses (compared by constant-pool reference). `File.isDirectory` was the
only one not in the first sample; it is used elsewhere in the firmware.
`File.renameTo`, single-byte `FileInputStream.read()` and `Math.max(long,long)` were not
found and were removed from the plugin.

## Not verified

- Displayable numbers 33 and 58 on this unit.
- The display layout as seen by a running system.
- Whether an update card still starts when the HMI is not running.
- Anything about `gal`'s internal object layouts.
