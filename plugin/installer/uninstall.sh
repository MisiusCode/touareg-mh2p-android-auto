#!/bin/ksh
# VW_AA_ClusterNav uninstall: restore the stock plugin list, remove the plugin file.
. "$MOD_PATH/common.sh"
echo "VW_AA_ClusterNav uninstall, unit $RELEASE_VERSION"
collect_logs
[ -z "$R" ] && mount -uw /mnt/app
if is_stock "$B"; then
    echo "plugin list is already stock"
else
    SRC=""
    if is_stock "$BK"; then SRC="$BK"; echo "using the stock list saved on the unit"
    elif is_stock "$MOD_PATH/backup/bundles.properties"; then SRC="$MOD_PATH/backup/bundles.properties"; echo "using the stock list saved on the card"
    elif is_ours "$B"; then strip_ours "$B" /tmp/vwaa_strip; is_stock /tmp/vwaa_strip && SRC=/tmp/vwaa_strip && echo "removing the mod's lines in place"
    fi
    if [ -n "$SRC" ] && put_file "$SRC" "$B" && is_stock "$B"; then
        echo "plugin list restored:"; grep -n 'Startup.Lateload' "$B"
    else
        echo "ERROR: could not restore the plugin list (no usable stock copy) - plugin file left in place"
        grep -n 'Startup.Lateload\|\.97 ' "$B"
        skip_modkit_hook; exit 0
    fi
fi
rm -f "$JD/$JAR" "$JD/$JAR.vwaa_new" "$B.vwaa_new" "$ULOG/vwaa_boot"
is_stock "$B" && rm -f "$BK"
sync
ls -l "$JD"
skip_modkit_hook
echo "done: uninstalled"
exit 0
