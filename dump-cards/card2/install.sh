#!/bin/ksh
#
# Touareg_Dump2 - identify an existing patch on an MH2p head unit.
# Runs as a mod of LawPaul's MH2p SD ModKit (CC BY-NC-SA 4.0, not for commercial use).
#
# Only READS from the head unit and only WRITES to the SD card ($MEDIA_PATH/Dump2).
# Copies: the patch's own files (autorunner, the startup wrapper, challenge.pub),
# small script / text / key files from the ota and persist partitions, the
# full-screen mod's marker files, and file listings of the whole unit.
# Media, navigation, speech and phone data are listed by name only, not copied.
#
# R is a path prefix used only for testing on a PC; on the car it is empty.

R="${DUMP_ROOT:-}"
MOD_ROOT="${MOD_PATH%/*}"
OUT="$MEDIA_PATH/Dump2"
export PATH="${PATH:-/bin:/usr/bin}:$R/mnt/app/armle/usr/bin:$R/mnt/app/armle/bin:/usr/bin:/bin"

have() { whence "$1" >/dev/null 2>&1; }

if [[ -z "${MEDIA_PATH:-}" || ! -d "$MEDIA_PATH" ]]; then
    echo "ERROR: MEDIA_PATH not set, nothing done"
    exit 1
fi
mkdir -p "$OUT/files" "$OUT/lists"
INFO="$OUT/info.txt"
echo "Touareg_Dump2 start" > "$INFO"
echo "release=${RELEASE_VERSION:-?} oem=${OEM:-?} sw=${SOFTWARE_VERSION:-?} media=$MEDIA_PATH" >> "$INFO"

# ---- listings ----
lst() { # name dir
    if [[ -e "$2" ]]; then ls -laR "$2" > "$OUT/lists/$1.txt" 2>&1; else echo "missing: $2" > "$OUT/lists/$1.txt"; fi
}
lst app          "$R/mnt/app"
lst ota          "$R/mnt/ota"
lst persist_new  "$R/mnt/persist_new"
lst misc1_top    "$R/mnt/misc1"
lst usr_bin      "$R/usr/bin"
lst ifs          "$R/ifs"
ls -la "$R/mnt/irc" "$R/mnt/crashlogs" > "$OUT/lists/irc_crashlogs_top.txt" 2>&1

# ---- file copies ----
COPIED=0; FAILED=0; SKIPPED=0
grab() { # src  -> $OUT/files/<same path>
    typeset src dst
    src="$1"
    [[ -f "$src" ]] || return 0
    dst="$OUT/files${src#$R}"
    mkdir -p "${dst%/*}" 2>/dev/null
    if cp -f "$src" "$dst" 2>>"$INFO"; then
        COPIED=$((COPIED + 1)); echo "copied: ${src#$R}" >> "$INFO"
    else
        FAILED=$((FAILED + 1)); echo "FAILED: ${src#$R}" >> "$INFO"
    fi
}
fsize() { ls -l "$1" 2>/dev/null | awk '{print $5}'; }
grab_small() { # src, only when under 2 MB
    typeset sz
    [[ -f "$1" ]] || return 0
    sz="$(fsize "$1")"
    if [[ "$sz" == +([0-9]) && "$sz" -lt 2097152 ]]; then grab "$1"; else SKIPPED=$((SKIPPED + 1)); echo "skipped (size $sz): ${1#$R}" >> "$INFO"; fi
}

# the patch itself
grab "$R/mnt/app/eso/bin/autorunner"
grab "$R/mnt/app/eso/bin/servicemgrmibhigh"
grab "$R/mnt/ota/challenge.pub"
grab "$R/ifs/challenge.pub"
ls -la "$R/usr/bin/autorunner" "$R/ifs/challenge.pub" "$R/mnt/ota/challenge.pub" >> "$INFO" 2>&1

# build identification of the firmware image
for f in build_parameters.txt img_ver.txt version_info.txt target.properties detailed_changelists_info.txt sentinel.input; do
    grab "$R/mnt/app/$f"
done

# full-screen mod markers and the layout file it replaces
for s in skin0 skin1 skin2 skin4; do
    for f in "$R"/mnt/app/eso/hmi/lsd/Resources/$s/*.txt "$R"/mnt/app/eso/hmi/lsd/Resources/$s/viewhandler*; do grab_small "$f"; done
done

# scripts, keys and small text files on the ota and persist partitions
if have find; then
    for d in "$R/mnt/ota" "$R/mnt/persist_new"; do
        find "$d" -type f \( -name '*.sh' -o -name '*.pub' -o -name '*.txt' -o -name '*.json' -o -name '*.cfg' -o -name '*.conf' -o -name '*.log' -o -name 'autorun*' \) 2>/dev/null
    done > "$OUT/lists/small_candidates.txt"
    while read f; do grab_small "$f"; done < "$OUT/lists/small_candidates.txt"
    # files anywhere under /mnt/app/eso/bin and /mnt/app/root that look like add-ons
    find "$R/mnt/app/root" "$R/mnt/app/sbin" -type f 2>/dev/null > "$OUT/lists/root_sbin_files.txt"
    while read f; do grab_small "$f"; done < "$OUT/lists/root_sbin_files.txt"
else
    echo "find missing: fixed paths only" >> "$INFO"
fi

echo "dump2: copied=$COPIED failed=$FAILED skipped=$SKIPPED" >> "$INFO"
sync 2>/dev/null

# keep the head unit untouched: stop the ModKit before it installs its own
# startup hook (it deletes this temporary file itself afterwards anyway)
if [[ -z "$R" ]]; then
    rm -f /mnt/persist_new/servicemgrmibhigh.sh
    echo "modkit hook: skipped ('error: servicemgrmibhigh.sh not found' in _ModKit_Update.log is intended)" >> "$INFO"
fi
echo "Touareg_Dump2 done" >> "$INFO"
echo "Touareg_Dump2 done: copied=$COPIED failed=$FAILED skipped=$SKIPPED, see Dump2/info.txt"
sync 2>/dev/null
exit 0
