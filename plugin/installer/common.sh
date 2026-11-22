# Shared by install.sh and uninstall.sh (VW_AA_ClusterNav). R is a test prefix, empty on the car.
R="${VWAA_ROOT:-}"
B="$R/mnt/app/eso/hmi/lsd/config/bundles.properties"
BK="$R/mnt/app/eso/hmi/lsd/config/bundles.properties.vwaa_stock"
JD="$R/mnt/app/eso/hmi/lsd/jars"
JAR=VWAndroidAutoNav.jar
ULOG="$R/mnt/ota/system/logs"
L_STOCK='^Bundle.Startup.Lateload = 61,62,95$'
L_OURS='^Bundle.Startup.Lateload = 61,62,95,97$'
N_OURS='^Bundle.Name.97 = VWAndroidAutoNav$'
A_OURS='^Bundle.Activator.97 = vwaa.Activator$'
export PATH="${PATH:-/bin:/usr/bin}:$R/mnt/app/armle/usr/bin:$R/mnt/app/armle/bin:/usr/bin:/bin"

is_stock() { # file: stock plugin list = stock late-load line, no bundle 97
    [ "$(grep -c "$L_STOCK" "$1" 2>/dev/null)" = "1" ] && ! grep -q '^Bundle\.[A-Za-z]*\.97 ' "$1"
}
is_ours() { # file: exactly our three edits
    [ "$(grep -c "$L_OURS" "$1" 2>/dev/null)" = "1" ] && [ "$(grep -c "$N_OURS" "$1")" = "1" ] && [ "$(grep -c "$A_OURS" "$1")" = "1" ]
}
strip_ours() { # in out: remove our edits from a patched file
    sed -e 's/^Bundle.Startup.Lateload = 61,62,95,97$/Bundle.Startup.Lateload = 61,62,95/' \
        -e '/^# VWAndroidAutoNav: /d' -e '/^Bundle.Name.97 = VWAndroidAutoNav$/d' -e '/^Bundle.Activator.97 = vwaa.Activator$/d' "$1" > "$2"
}
put_file() { # src dst: write next to the target first, then replace it
    cp -f "$1" "$2.vwaa_new" || return 1
    sync
    if whence mv >/dev/null 2>&1; then
        mv -f "$2.vwaa_new" "$2" || return 1
    else
        cp -f "$2.vwaa_new" "$2" || return 1
        rm -f "$2.vwaa_new"
    fi
    sync
}
collect_logs() { # copy the plugin's logs from the unit to the card
    mkdir -p "$MEDIA_PATH/Logs/vwaa_unit" 2>/dev/null
    for f in "$ULOG"/vwaa*; do
        [ -f "$f" ] && cp -f "$f" "$MEDIA_PATH/Logs/vwaa_unit/" && echo "collected ${f##*/}"
    done
}
skip_modkit_hook() {
    # The ModKit would now replace the unit's startup wrapper with its own. This unit already
    # has a different startup patch, and this mod needs no hook, so that step is skipped: the
    # ModKit stops when its temporary copy is missing (it deletes that copy itself anyway).
    if [ -z "$R" ]; then
        rm -f /mnt/persist_new/servicemgrmibhigh.sh
        echo "modkit hook skipped ('error: servicemgrmibhigh.sh not found' in _ModKit_Update.log is intended)"
    fi
}
