#!/system/bin/sh

MODDIR=${0%/*}
MAGISKTMP=$(magisk --path)

nethunter_mode_enabled() {
    local value=$(magisk --sqlite "SELECT value FROM settings WHERE key='nethunter_mode'" | grep "value=" | cut -d= -f2)
    [ "$value" = "1" ]
}

wait_for_boot() {
    local count=0
    while [ $count -lt 60 ]; do
        if [ "$(getprop sys.boot_completed)" = "1" ]; then
            sleep 5
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done
    return 1
}

apply_nethunter_fixes() {
    log -t "NetHunter" "Applying NetHunter Mode fixes..."
    
    sh "$MODDIR/nethunter_fix.sh" run
    
    if [ ! -d "/data/local/.nethunter" ]; then
        mkdir -p /data/local/.nethunter
        touch /data/local/.nethunter/.nomedia
    fi
    
    for pkg in com.offsec.nhterm com.offsec.nethunter; do
        pm hide "$pkg" 2>/dev/null || true
    done
    
    (
        while true; do
            sleep 60
            for path in /data/data/com.offsec.nhterm /data/data/com.offsec.nethunter; do
                if [ -d "$path" ]; then
                    chmod -R 755 "$path/files" 2>/dev/null
                fi
            done
        done
    ) &
    
    log -t "NetHunter" "NetHunter Mode fixes applied successfully"
}

if nethunter_mode_enabled; then
    wait_for_boot
    apply_nethunter_fixes
else
    log -t "NetHunter" "NetHunter Mode is disabled, skipping fixes"
fi

