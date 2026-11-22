#!/bin/ksh
#
# Touareg_Dump - copy firmware files from an MH2p head unit to the update media.
# Runs as a mod of LawPaul's MH2p SD ModKit (CC BY-NC-SA 4.0, not for commercial use).
#
# This script only READS from the head unit and only WRITES to the SD card
# ($MEDIA_PATH/Dump). It installs nothing.
#
# Marker files next to the Update/ folder:
#   full_dump.txt        also copy the whole /mnt/app/eso tree (large, slow)
#   allow_modkit.txt     let the ModKit install its own startup hook afterwards
#                        (default: the hook step is skipped, see the end of this file)
#
# R is a path prefix used only for testing on a PC; on the car it is empty.

R="${DUMP_ROOT:-}"
MOD_ROOT="${MOD_PATH%/*}"
OUT="$MEDIA_PATH/Dump"
export PATH="${PATH:-/bin:/usr/bin}:$R/mnt/app/armle/usr/bin:$R/mnt/app/armle/bin:/usr/bin:/bin"

have() { whence "$1" >/dev/null 2>&1; }

if [[ -z "${MEDIA_PATH:-}" || ! -d "$MEDIA_PATH" ]]; then
    print "ERROR: MEDIA_PATH not set, nothing done"
    exit 1
fi
mkdir -p "$OUT/files" "$OUT/lists"
INFO="$OUT/info.txt"

print "Touareg_Dump start" > "$INFO"
{
    print "release=${RELEASE_VERSION:-?} region=${REGION:-?} oem=${OEM:-?} type=${TYPE:-?} sw=${SOFTWARE_VERSION:-?}"
    print "modkit=${MODKIT_VERSION:-?} media=$MEDIA_PATH"
    print "--- date";   date 2>&1
    print "--- uname";  have uname && uname -a 2>&1
    print "--- tools"
    for t in tar pax cp find grep awk sed wc cmp sum cksum od strings pidin df mount use; do
        if have $t; then print "$t: yes"; else print "$t: no"; fi
    done
    print "--- mount";  have mount && mount 2>&1
    print "--- df";     have df && df -k 2>&1
    print "--- pc version raw"
    [[ -x "$R/mnt/app/armle/usr/bin/pc" ]] && "$R/mnt/app/armle/usr/bin/pc" b:46924065:401 2>&1
    print "--- pidin info"; have pidin && pidin info 2>&1
} >> "$INFO" 2>&1

# ---- listings (text only) ----
lst() { # name dir
    if [[ -e "$2" ]]; then ls -lR "$2" > "$OUT/lists/$1.txt" 2>&1; else print "missing: $2" > "$OUT/lists/$1.txt"; fi
}
lst ifs            "$R/ifs"
lst eso_bin        "$R/mnt/app/eso/bin"
lst eso_lib        "$R/mnt/app/eso/lib"
lst eso_hmi_lsd    "$R/mnt/app/eso/hmi/lsd"
lst armle_lib      "$R/mnt/app/armle/lib"
lst armle_usr_lib  "$R/mnt/app/armle/usr/lib"
lst etc            "$R/etc"
ls -l "$R/mnt/app" "$R/mnt/app/eso" "$R/mnt" > "$OUT/lists/top.txt" 2>&1

# ---- file copies ----
COPIED=0; FAILED=0
grab() { # src  -> $OUT/files/<same path>
    typeset src dst
    src="$1"
    [[ -f "$src" ]] || return 0
    dst="$OUT/files${src#$R}"
    mkdir -p "${dst%/*}" 2>/dev/null
    if cp -f "$src" "$dst" 2>>"$INFO"; then
        COPIED=$((COPIED + 1)); print "copied: ${src#$R}" >> "$INFO"
    else
        FAILED=$((FAILED + 1)); print "FAILED: ${src#$R}" >> "$INFO"
    fi
}

# Android Auto receiver and CarPlay manager (original and any earlier backup names)
for f in gal gal.real gal0 dio_manager dio_manager.real dio_manager0; do
    grab "$R/mnt/app/eso/bin/apps/$f"
done
grab "$R/mnt/app/eso/bin/servicemgrmibhigh"
grab "$R/mnt/app/eso/bin/servicemgrmibhigh0"

# HMI Java: plug-in jars, the main jar, the ROM class image, start script, small configs
for f in "$R"/mnt/app/eso/hmi/lsd/jars/*.jar "$R"/mnt/app/eso/hmi/lsd/jars/*/*.jar; do grab "$f"; done
grab "$R/mnt/app/eso/hmi/lsd/lsd.jar"
grab "$R/ifs/lsd.jxe"
for f in "$R"/mnt/app/eso/hmi/lsd/*.sh "$R"/mnt/app/eso/hmi/lsd/*.properties "$R"/mnt/app/eso/hmi/lsd/*.json \
         "$R"/mnt/app/eso/hmi/lsd/*.xml "$R"/mnt/app/eso/hmi/lsd/*.cfg "$R"/mnt/app/eso/hmi/lsd/*.conf "$R"/mnt/app/eso/hmi/lsd/*.txt; do
    grab "$f"
done

# Libraries the Android Auto hook links against, and display / graphics configuration
if have find; then
    for pat in 'libautoreceiver*' 'libprotobuf*' 'libscreen*' 'libgal*' 'libnvmedia*' \
               'graphics*.conf' '*display*.conf' '*screen*.conf' 'scaling*.conf' '*kombi*' '*Kombi*'; do
        find "$R/mnt/app/eso" "$R/mnt/app/armle" "$R/etc" "$R/usr/lib" "$R/lib" -name "$pat" -type f 2>/dev/null | while read f; do
            print "$f"
        done
    done > "$OUT/lists/found.txt" 2>/dev/null
    while read f; do grab "$f"; done < "$OUT/lists/found.txt"
else
    print "find missing: fixed library paths only" >> "$INFO"
    for f in "$R"/mnt/app/eso/lib/libautoreceiver* "$R"/mnt/app/armle/lib/libautoreceiver* "$R"/mnt/app/armle/usr/lib/libautoreceiver* \
             "$R"/mnt/app/armle/lib/libscreen* "$R"/mnt/app/armle/usr/lib/libscreen* "$R"/usr/lib/libscreen* \
             "$R"/mnt/app/armle/lib/libprotobuf* "$R"/mnt/app/armle/usr/lib/libprotobuf* "$R"/mnt/app/eso/lib/libprotobuf*; do
        grab "$f"
    done
fi

print "core dump: copied=$COPIED failed=$FAILED" >> "$INFO"

# ---- optional full copy of /mnt/app/eso ----
if [[ -e "$MOD_ROOT/full_dump.txt" ]]; then
    print "--- full dump of /mnt/app/eso" >> "$INFO"
    if have tar; then
        ( cd "$R/mnt/app" && tar -cf "$OUT/eso.tar" eso ) >> "$INFO" 2>&1
        print "full dump: tar exit=$?" >> "$INFO"
    elif have pax; then
        ( cd "$R/mnt/app" && pax -w -f "$OUT/eso.pax" eso ) >> "$INFO" 2>&1
        print "full dump: pax exit=$?" >> "$INFO"
    else
        mkdir -p "$OUT/full"
        cp -R "$R/mnt/app/eso" "$OUT/full/" >> "$INFO" 2>&1
        print "full dump: cp exit=$? (links and permissions are lost on a FAT card)" >> "$INFO"
    fi
fi

{ print "--- df after"; have df && df -k "$MEDIA_PATH"; } >> "$INFO" 2>&1
sync 2>/dev/null

# ---- keep the head unit untouched ----
# After the mods have run, the ModKit installs its own startup hook on the
# unit (it wraps /mnt/app/eso/bin/servicemgrmibhigh). A dump does not need
# that. The ModKit stops before that step when its temporary copy of the
# wrapper is missing, and it deletes that temporary copy itself afterwards
# anyway, so removing it here leaves the unit as it was.
if [[ -e "$MOD_ROOT/allow_modkit.txt" ]]; then
    print "modkit hook: left to the ModKit (allow_modkit.txt present)" >> "$INFO"
elif [[ -z "$R" ]]; then
    rm -f /mnt/persist_new/servicemgrmibhigh.sh
    print "modkit hook: skipped (expect 'error: servicemgrmibhigh.sh not found' in _ModKit_Update.log, that is intended)" >> "$INFO"
fi

print "Touareg_Dump done" >> "$INFO"
print "Touareg_Dump done: copied=$COPIED failed=$FAILED, see Dump/info.txt"
sync 2>/dev/null
exit 0
