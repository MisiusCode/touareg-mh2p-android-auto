#!/bin/ksh
#
# Touareg_Dump3 - backup-grade dump of an MH2p head unit to the update media.
# Runs as a mod of LawPaul's MH2p SD ModKit (CC BY-NC-SA 4.0, not for commercial use).
#
# Only READS from the head unit and only WRITES to the SD card ($MEDIA_PATH/Dump3).
# Order: small and important things first, the large full copy last, with a sync
# after each stage, so an interrupted run still leaves the earlier stages on the card.
#
#   Dump3/files/     programs, libraries and config files, same paths as on the unit
#   Dump3/lists/     file listings
#   Dump3/private/   licence and settings data bound to this car. Keep it, do not share it.
#   Dump3/app.tar    full copy of the app partition (skipped if no_full.txt exists)
#
# R is a path prefix used only for testing on a PC; on the car it is empty.

R="${DUMP_ROOT:-}"
MOD_ROOT="${MOD_PATH%/*}"
OUT="$MEDIA_PATH/Dump3"
export PATH="${PATH:-/bin:/usr/bin}:$R/mnt/app/armle/usr/bin:$R/mnt/app/armle/bin:/usr/bin:/bin"

have() { whence "$1" >/dev/null 2>&1; }

if [[ -z "${MEDIA_PATH:-}" || ! -d "$MEDIA_PATH" ]]; then
    echo "ERROR: MEDIA_PATH not set, nothing done"
    exit 1
fi
mkdir -p "$OUT/files" "$OUT/lists" "$OUT/private"
INFO="$OUT/info.txt"
echo "Touareg_Dump3 start" > "$INFO"
echo "release=${RELEASE_VERSION:-?} oem=${OEM:-?} sw=${SOFTWARE_VERSION:-?} media=$MEDIA_PATH" >> "$INFO"
{ echo "== mount"; have mount && mount; echo "== df"; have df && df -k; } >> "$INFO" 2>&1

COPIED=0; FAILED=0
grab_to() { # src destroot
    typeset src dst
    src="$1"
    [[ -f "$src" ]] || return 0
    dst="$2${src#$R}"
    mkdir -p "${dst%/*}" 2>/dev/null
    if cp -f "$src" "$dst" 2>>"$INFO"; then
        COPIED=$((COPIED + 1)); echo "copied: ${src#$R}" >> "$INFO"
    else
        FAILED=$((FAILED + 1)); echo "FAILED: ${src#$R}" >> "$INFO"
    fi
}
grab()  { grab_to "$1" "$OUT/files"; }
stage() { echo "== stage $1: copied=$COPIED failed=$FAILED" >> "$INFO"; sync 2>/dev/null; }

# ---- stage 1: listings ----
lst() { if [[ -e "$2" ]]; then ls -laR "$2" > "$OUT/lists/$1.txt" 2>&1; else echo "missing: $2" > "$OUT/lists/$1.txt"; fi; }
lst app "$R/mnt/app"; lst etc "$R/etc"; lst ifs "$R/ifs"; lst usr_lib "$R/usr/lib"; lst lib "$R/lib"
lst ota "$R/mnt/ota"; lst persist_new "$R/mnt/persist_new"; lst mnt_system "$R/mnt/system"
ls -la "$R/mnt" "$R/dev" > "$OUT/lists/mnt_dev.txt" 2>&1
stage 1

# ---- stage 2: system config (plugin list, display layout, phone integration) ----
# The system config partition is not always mounted during an update. If it is
# missing it is mounted READ-ONLY here and unmounted again at the end.
MOUNTED_SYS=0
if [[ -z "$R" && ! -e /mnt/system/etc && ! -e /etc/eso/production ]]; then
    if mount -o ro,noatime,nosuid,noexec /dev/fs0p1 /mnt/system >> "$INFO" 2>&1; then
        MOUNTED_SYS=1; echo "mounted /mnt/system read-only" >> "$INFO"
    else
        echo "could not mount /mnt/system (config files there are skipped)" >> "$INFO"
    fi
fi
for d in "$R/etc/eso/production" "$R/mnt/system/etc/eso/production" "$R/etc/eso/presets" "$R/mnt/system/etc/eso/presets" \
         "$R/etc/eso" "$R/mnt/system/etc/eso" "$R/etc/config" "$R/mnt/system/etc/config" "$R/etc" "$R/mnt/system/etc"; do
    for f in "$d"/*.json "$d"/*.conf "$d"/*.cfg "$d"/*.properties "$d"/*.xml "$d"/*.sh "$d"/*.txt; do grab "$f"; done
done
[[ -e "$R/mnt/system" ]] && ls -laR "$R/mnt/system/etc" > "$OUT/lists/mnt_system_etc.txt" 2>&1
for f in "$R"/mnt/app/eso/hmi/lsd/config/* "$R"/mnt/app/eso/hmi/lsd/*.sh "$R"/mnt/app/eso/hmi/lsd/*.conf \
         "$R"/mnt/app/eso/hmi/lsd/*.properties "$R"/mnt/app/eso/ulp/conf/*; do grab "$f"; done
for f in build_parameters.txt img_ver.txt version_info.txt target.properties detailed_changelists_info.txt; do grab "$R/mnt/app/$f"; done
stage 2

# ---- stage 3: Android Auto / CarPlay programs, their libraries, the HMI image ----
for f in gal gal.real gal0 dio_manager dio_manager.real dio_manager0 mibkombisync2service dmdt; do grab "$R/mnt/app/eso/bin/apps/$f"; done
for f in "$R"/mnt/app/eso/bin/apps/*.json "$R"/mnt/app/eso/bin/apps/*.sh "$R"/mnt/app/eso/bin/*.sh; do grab "$f"; done
grab "$R/mnt/app/eso/bin/servicemgrmibhigh"; grab "$R/mnt/app/eso/bin/servicemgrmibhigh0"; grab "$R/mnt/app/eso/bin/autorunner"
for f in "$R"/mnt/app/eso/hmi/lsd/jars/*.jar "$R"/mnt/app/eso/hmi/lsd/jars/*.zip; do grab "$f"; done
for n in libautoreceiver libprotobuf3full libprotobuflite libairplay libdisplayinit libNmeSDK; do
    for f in "$R"/mnt/app/eso/lib/$n* "$R"/mnt/app/armle/lib/$n* "$R"/mnt/app/armle/usr/lib/$n*; do grab "$f"; done
done
for f in "$R"/mnt/app/eso/lib/factories/*kombi* "$R"/mnt/app/eso/lib/factories/*androidauto* "$R"/mnt/app/eso/lib/factories/*carplay* \
         "$R"/mnt/app/eso/lib/factories/*displaymanag* "$R"/usr/lib/libscreen* "$R"/lib/libnvmedia* "$R"/usr/lib/libnvparser* "$R"/lib/libnvparser*; do grab "$f"; done
grab "$R/ifs/lsd.jxe"
for s in skin0 skin1 skin2 skin4; do
    grab "$R/mnt/app/eso/hmi/lsd/Resources/$s/info.txt"; grab "$R/mnt/app/eso/hmi/lsd/Resources/$s/viewhandler.zip"
done
stage 3

# ---- stage 4: licence and settings data, bound to this car (kept apart) ----
for f in "$R"/mnt/persist_new/fec/*; do grab_to "$f" "$OUT/private"; done
grab_to "$R/mnt/persist_new/persistence/persistence.sqlite" "$OUT/private"
stage 4

# ---- stage 5: full copy of the app partition (large, last) ----
if [[ -e "$MOD_ROOT/no_full.txt" ]]; then
    echo "full copy: skipped (no_full.txt)" >> "$INFO"
elif have tar; then
    echo "full copy: tar start" >> "$INFO"; sync 2>/dev/null
    ( cd "$R/mnt" && tar -cf "$OUT/app.tar" app ) >> "$INFO" 2>&1
    echo "full copy: tar exit=$?" >> "$INFO"
    ls -l "$OUT/app.tar" >> "$INFO" 2>&1
else
    echo "full copy: no tar on this unit, skipped" >> "$INFO"
fi
stage 5

if [[ "$MOUNTED_SYS" == 1 ]]; then
    umount /mnt/system >> "$INFO" 2>&1 && echo "unmounted /mnt/system" >> "$INFO"
fi

# keep the head unit untouched: stop the ModKit before it installs its own
# startup hook (it deletes this temporary file itself afterwards anyway)
if [[ -z "$R" ]]; then
    rm -f /mnt/persist_new/servicemgrmibhigh.sh
    echo "modkit hook: skipped ('error: servicemgrmibhigh.sh not found' in _ModKit_Update.log is intended)" >> "$INFO"
fi
{ echo "== df after"; have df && df -k "$MEDIA_PATH"; } >> "$INFO" 2>&1
echo "Touareg_Dump3 done" >> "$INFO"
echo "Touareg_Dump3 done: copied=$COPIED failed=$FAILED, see Dump3/info.txt"
sync 2>/dev/null
exit 0
