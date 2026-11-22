#!/bin/ksh
#
# Touareg_Dump4 - system config partition + full copy of the app partition.
# Runs as a mod of LawPaul's MH2p SD ModKit (CC BY-NC-SA 4.0, not for commercial use).
#
# Only READS from the head unit and only WRITES to the SD card ($MEDIA_PATH/Dump4).
#
# Stage 1: the real system config. During an update the unit runs from a temporary
#          system, so /etc is not the real one. The flash partitions /dev/fs0p0..p4
#          are mounted READ-ONLY one at a time, listed, their small config files
#          copied, and unmounted again. Nothing is mounted writable.
# Stage 2: full copy of /mnt/app, trying several methods (the unit's tar failed
#          when started from the ramdisk).  Skipped if no_full.txt exists.
#
# R is a path prefix used only for testing on a PC; on the car it is empty.

R="${DUMP_ROOT:-}"
MOD_ROOT="${MOD_PATH%/*}"
OUT="$MEDIA_PATH/Dump4"
export PATH="${PATH:-/bin:/usr/bin}:$R/mnt/app/armle/usr/bin:$R/mnt/app/armle/bin:/usr/bin:/bin"

have() { whence "$1" >/dev/null 2>&1; }
fsize() { ls -l "$1" 2>/dev/null | awk '{print $5}'; }

if [[ -z "${MEDIA_PATH:-}" || ! -d "$MEDIA_PATH" ]]; then
    echo "ERROR: MEDIA_PATH not set, nothing done"
    exit 1
fi
mkdir -p "$OUT/lists"
INFO="$OUT/info.txt"
echo "Touareg_Dump4 start" > "$INFO"
echo "release=${RELEASE_VERSION:-?} oem=${OEM:-?} sw=${SOFTWARE_VERSION:-?} media=$MEDIA_PATH" >> "$INFO"
{ echo "== tools"; for t in tar cpio cp find mount umount; do if have $t; then echo "$t: yes"; else echo "$t: no"; fi; done
  echo "== mount"; have mount && mount; echo "== fs0 devices"; ls -la "$R"/dev/fs0*; } >> "$INFO" 2>&1

# ---------------- stage 1: system config partitions ----------------
copy_cfg() { # srcdir destdir : copy small config-type files, keeping the tree
    typeset f rel dst sz
    find "$1" -type f \( -name '*.json' -o -name '*.conf' -o -name '*.cfg' -o -name '*.properties' -o -name '*.xml' \
         -o -name '*.sh' -o -name '*.txt' -o -name '*.ini' -o -name '*.rc' -o -name '*.script' \) 2>/dev/null |
    while read f; do
        sz="$(fsize "$f")"
        if [[ "$sz" == +([0-9]) && "$sz" -lt 2097152 ]]; then
            rel="${f#$1}"; dst="$2$rel"
            mkdir -p "${dst%/*}" 2>/dev/null
            if cp -f "$f" "$dst" 2>>"$INFO"; then echo "copied: $rel" >> "$INFO"; else echo "FAILED: $rel" >> "$INFO"; fi
        fi
    done
}

MP="$R/mnt/dump4sys"
for p in 1 0 2 3 4; do
    DEV="$R/dev/fs0p$p"
    echo "== fs0p$p" >> "$INFO"
    if [[ -n "$R" ]]; then
        SRC="$R/fake_fs0p$p"; [[ -d "$SRC" ]] || { echo "not present" >> "$INFO"; continue; }
    else
        [[ -e "$DEV" ]] || { echo "not present" >> "$INFO"; continue; }
        if ! mount -o ro,noatime,nosuid,noexec "$DEV" "$MP" >> "$INFO" 2>&1; then
            echo "read-only mount failed, skipped" >> "$INFO"; continue
        fi
        SRC="$MP"
    fi
    echo "mounted read-only" >> "$INFO"
    ls -laR "$SRC" > "$OUT/lists/fs0p$p.txt" 2>&1
    copy_cfg "$SRC" "$OUT/sys_p$p"
    if [[ -z "$R" ]]; then
        umount "$MP" >> "$INFO" 2>&1 && echo "unmounted" >> "$INFO"
    fi
    sync 2>/dev/null
done
# already-mounted system paths, if the unit has them
for d in /mnt/system /mnt/efs-system /mnt/efs-persist; do
    [[ -e "$R$d" ]] && { ls -laR "$R$d" > "$OUT/lists/$(echo $d | sed 's|/|_|g').txt" 2>&1; copy_cfg "$R$d" "$OUT/sys_mounted$d"; }
done
echo "== stage 1 done" >> "$INFO"; sync 2>/dev/null

# ---------------- stage 2: full copy of /mnt/app ----------------
ok_size() { typeset s; s="$(fsize "$1")"; [[ "$s" == +([0-9]) && "$s" -gt 1048576 ]]; }
if [[ -e "$MOD_ROOT/no_full.txt" ]]; then
    echo "full copy: skipped (no_full.txt)" >> "$INFO"
else
    DONE=0
    if have tar; then
        echo "full copy: tar, started inside /mnt/app" >> "$INFO"
        ( cd "$R/mnt/app" && tar -cf "$OUT/app.tar" . ) >> "$INFO" 2>&1; echo "exit=$?" >> "$INFO"
        if ok_size "$OUT/app.tar"; then DONE=1; else rm -f "$OUT/app.tar"; fi
        if [[ $DONE == 0 ]]; then
            echo "full copy: tar, started on the card" >> "$INFO"
            ( cd "$OUT" && tar -cf app.tar "$R/mnt/app" ) >> "$INFO" 2>&1; echo "exit=$?" >> "$INFO"
            if ok_size "$OUT/app.tar"; then DONE=1; else rm -f "$OUT/app.tar"; fi
        fi
    fi
    if [[ $DONE == 0 ]] && have cpio && have find; then
        echo "full copy: cpio" >> "$INFO"
        ( cd "$R/mnt/app" && find . -print | cpio -o > "$OUT/app.cpio" ) 2>>"$INFO"; echo "exit=$?" >> "$INFO"
        if ok_size "$OUT/app.cpio"; then DONE=1; else rm -f "$OUT/app.cpio"; fi
    fi
    if [[ $DONE == 0 ]]; then
        echo "full copy: plain folder copy (links and permissions are not kept on a FAT card)" >> "$INFO"
        mkdir -p "$OUT/app_full"
        cp -R "$R/mnt/app/." "$OUT/app_full/" >> "$INFO" 2>&1; echo "exit=$?" >> "$INFO"
        DONE=2
    fi
    echo "full copy: result=$DONE (1 archive, 2 folder copy)" >> "$INFO"
    ls -l "$OUT" >> "$INFO" 2>&1
fi
sync 2>/dev/null

# keep the head unit untouched: stop the ModKit before it installs its own
# startup hook (it deletes this temporary file itself afterwards anyway)
if [[ -z "$R" ]]; then
    rm -f /mnt/persist_new/servicemgrmibhigh.sh
    echo "modkit hook: skipped ('error: servicemgrmibhigh.sh not found' in _ModKit_Update.log is intended)" >> "$INFO"
fi
{ echo "== mount after"; have mount && mount; echo "== df after"; have df && df -k "$MEDIA_PATH"; } >> "$INFO" 2>&1
echo "Touareg_Dump4 done" >> "$INFO"
echo "Touareg_Dump4 done, see Dump4/info.txt"
sync 2>/dev/null
exit 0
