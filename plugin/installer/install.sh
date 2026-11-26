#!/bin/ksh
#
# VW_AA_ClusterNav - Android Auto turn-by-turn on the instrument cluster and HUD.
# VW Touareg CR, MH2p_ER_VWG36_P2869 only. Runs as a mod of LawPaul's MH2p SD ModKit.
#
# Changes on the unit:
#   + /mnt/app/eso/hmi/lsd/jars/VWAndroidAutoNav.jar           (new file)
#   ~ /mnt/app/eso/hmi/lsd/config/bundles.properties           (bundle 97 appended, started last)
#   + /mnt/app/eso/hmi/lsd/config/bundles.properties.vwaa_stock (copy of the stock file)
# Nothing else. To remove: put an empty file "uninstall.txt" in Mods/VW_AA_ClusterNav and run the card again.
#
# Installer structure after a78c7 / plafp touareg-cn-carplay-mods (GPL-3.0).

. "$MOD_PATH/common.sh"
echo "VW_AA_ClusterNav install, unit $RELEASE_VERSION"

if [ -z "$R" ] && [ "$RELEASE_VERSION" != "MH2p_ER_VWG36_P2869" ]; then
    echo "abort: built for MH2p_ER_VWG36_P2869, this unit is $RELEASE_VERSION"; skip_modkit_hook; exit 0
fi
touch "$MOD_PATH/test.txt"
if [ ! -e "$MOD_PATH/test.txt" ]; then echo "abort: card is write protected"; skip_modkit_hook; exit 0; fi
rm -f "$MOD_PATH/test.txt"
[ ! -f "$MOD_PATH/$JAR" ] && { echo "abort: $JAR missing on card"; skip_modkit_hook; exit 0; }
[ ! -f "$B" ] && { echo "abort: $B not found"; skip_modkit_hook; exit 0; }
[ ! -d "$JD" ] && { echo "abort: $JD not found"; skip_modkit_hook; exit 0; }

collect_logs

# 1) find the stock plugin list
STOCK=/tmp/vwaa_stock
rm -f $STOCK
if is_stock "$B"; then
    cp -f "$B" $STOCK; echo "unit has the stock plugin list"
elif is_ours "$B"; then
    if is_stock "$BK"; then
        cp -f "$BK" $STOCK; echo "unit already has this mod: using the stock list saved on the unit"
    else
        strip_ours "$B" $STOCK; echo "unit already has this mod: rebuilding the stock list from it"
    fi
else
    echo "abort: plugin list is neither stock nor this mod (another mod uses it?) - nothing changed"
    grep -n 'Startup.Lateload\|\.97 ' "$B"
    skip_modkit_hook; exit 0
fi
if ! is_stock $STOCK; then echo "abort: could not establish the stock plugin list - nothing changed"; skip_modkit_hook; exit 0; fi

# 2) build the new list and check it before touching the unit
NEW=/tmp/vwaa_new
sed -e 's/^Bundle.Startup.Lateload = 61,62,95$/Bundle.Startup.Lateload = 61,62,95,97/' $STOCK > $NEW
echo "" >> $NEW
echo "# VWAndroidAutoNav: Android Auto turn-by-turn -> cluster/HUD (card mod VW_AA_ClusterNav)" >> $NEW
echo "Bundle.Name.97 = VWAndroidAutoNav" >> $NEW
echo "Bundle.Activator.97 = vwaa.Activator" >> $NEW
if ! is_ours $NEW; then echo "abort: edit check 1 failed - nothing changed"; skip_modkit_hook; exit 0; fi
if [ "$(grep -c '^Bundle\.' $NEW)" != "$(( $(grep -c '^Bundle\.' $STOCK) + 2 ))" ]; then echo "abort: edit check 2 failed - nothing changed"; skip_modkit_hook; exit 0; fi
strip_ours $NEW /tmp/vwaa_round
if [ "$(grep -c '^Bundle\.' /tmp/vwaa_round)" != "$(grep -c '^Bundle\.' $STOCK)" ] || ! is_stock /tmp/vwaa_round; then
    echo "abort: edit check 3 failed - nothing changed"; skip_modkit_hook; exit 0
fi

# 3) backups: on the card and on the unit
mkdir -p "$MOD_PATH/backup"
[ ! -e "$MOD_PATH/backup/bundles.properties" ] && cp -f $STOCK "$MOD_PATH/backup/bundles.properties" && echo "stock list saved on the card"
[ -z "$R" ] && mount -uw /mnt/app
if ! is_stock "$BK"; then
    cp -f $STOCK "$BK" && sync
    if ! is_stock "$BK"; then echo "abort: could not write the on-unit backup - nothing else changed"; skip_modkit_hook; exit 0; fi
    echo "stock list saved on the unit"
fi

# 4) the plugin file
cp -f "$MOD_PATH/$JAR" "$JD/$JAR.vwaa_new" && sync
if [ "$(ls -l "$JD/$JAR.vwaa_new" | awk '{print $5}')" != "$(ls -l "$MOD_PATH/$JAR" | awk '{print $5}')" ]; then
    rm -f "$JD/$JAR.vwaa_new"; echo "abort: plugin file copy is incomplete - plugin list not changed"; skip_modkit_hook; exit 0
fi
if whence mv >/dev/null 2>&1; then
    mv -f "$JD/$JAR.vwaa_new" "$JD/$JAR"
else
    cp -f "$JD/$JAR.vwaa_new" "$JD/$JAR"; rm -f "$JD/$JAR.vwaa_new"
fi
sync
if [ ! -f "$JD/$JAR" ] || [ "$(ls -l "$JD/$JAR" | awk '{print $5}')" != "$(ls -l "$MOD_PATH/$JAR" | awk '{print $5}')" ]; then
    rm -f "$JD/$JAR"; echo "abort: plugin file is missing or incomplete on the unit - plugin list not changed"; skip_modkit_hook; exit 0
fi
chmod 644 "$JD/$JAR"
ls -l "$JD"

# 5) the plugin list
if put_file $NEW "$B" && is_ours "$B"; then
    echo "plugin list updated:"
    grep -n 'Startup.Lateload' "$B"; grep -n '\.97 ' "$B"
else
    echo "ERROR: plugin list write failed - restoring the stock list"
    cp -f "$BK" "$B"; sync
    rm -f "$JD/$JAR"
    if is_stock "$B"; then echo "stock list restored, plugin removed"; else echo "ERROR: restore check failed, run the card again with uninstall.txt"; fi
    skip_modkit_hook; exit 0
fi

# 5b) Part B preparation: read-only facts about the unit, written to the card. Nothing on the
#     unit is changed here and a failure of any line is ignored.
(
    echo "== df"; df -k "$R/mnt/app" "$R/mnt/ota" "$R/tmp"
    echo "== apps"; ls -la "$R/mnt/app/eso/bin/apps"
    echo "== apps/cluster"; ls -la "$R/mnt/app/eso/bin/apps/cluster"
    echo "== eso/bin"; ls -la "$R/mnt/app/eso/bin"
    echo "== eso/lib"; ls -la "$R/mnt/app/eso/lib"
    echo "== jars"; ls -la "$JD"
    echo "== screen library"; ls -la "$R"/usr/lib/libscreen* "$R"/lib/libscreen* "$R"/mnt/app/armle/usr/lib/libscreen* "$R"/mnt/app/armle/lib/libscreen* "$R"/proc/boot/libscreen* "$R"/ifs/lib*/libscreen* "$R"/ifs/usr/lib/libscreen*
    echo "== nvmedia"; ls -la "$R"/mnt/app/armle/usr/lib/libnvmedia* "$R"/usr/lib/libnvmedia* "$R"/mnt/app/armle/lib/libnvmedia*
    echo "== ota"; ls -la "$R/mnt/ota"
) > "$MEDIA_PATH/Logs/partb_info.txt" 2>&1
echo "part B info written"

# 6) fresh start for the self-disable counter
rm -f "$ULOG/vwaa_boot"
sync
skip_modkit_hook
echo "done: installed"
exit 0
