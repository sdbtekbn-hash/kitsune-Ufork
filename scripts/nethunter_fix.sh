#!/system/bin/sh

NETHUNTER_PACKAGES="
com.offsec.nhterm
com.offsec.nethunter
ru.meefik.linuxdeploy
"

NETHUNTER_PATHS="
/data/data/com.offsec.nhterm
/data/data/com.offsec.nethunter
/data/local/nhsystem
/data/local/kali-armhf
"

fix_nethunter_permissions() {
    echo "Fixing NetHunter permissions..."
    
    for path in $NETHUNTER_PATHS; do
        if [ -d "$path" ]; then
            echo "Fixing: $path"
            chown -R root:root "$path" 2>/dev/null
            chmod -R 755 "$path" 2>/dev/null
            
            if [ -d "$path/files" ]; then
                chmod -R 755 "$path/files" 2>/dev/null
            fi
            
            if [ -d "$path/files/usr/bin" ]; then
                chmod -R 755 "$path/files/usr/bin" 2>/dev/null
                chmod +x "$path/files/usr/bin"/* 2>/dev/null
            fi
        fi
    done
}

fix_selinux_contexts() {
    echo "Fixing SELinux contexts..."
    
    for path in $NETHUNTER_PATHS; do
        if [ -d "$path" ]; then
            chcon -R u:object_r:app_data_file:s0 "$path" 2>/dev/null
            
            if [ -d "$path/files/usr/bin" ]; then
                chcon u:object_r:system_file:s0 "$path/files/usr/bin"/* 2>/dev/null
            fi
        fi
    done
}

add_to_denylist() {
    echo "Adding NetHunter packages to DenyList..."
    
    for pkg in $NETHUNTER_PACKAGES; do
        magisk --denylist add "$pkg" 2>/dev/null
        echo "Added: $pkg"
    done
}

fix_shell_scripts() {
    echo "Fixing NetHunter shell scripts..."
    
    NH_TERM="/data/data/com.offsec.nhterm/files"
    
    if [ -d "$NH_TERM" ]; then
        if [ -f "$NH_TERM/usr/bin/kali" ]; then
            sed -i '1s|^.*|#!/system/bin/sh|' "$NH_TERM/usr/bin/kali" 2>/dev/null
            chmod +x "$NH_TERM/usr/bin/kali"
        fi
        
        if [ -f "$NH_TERM/scripts/bootkali" ]; then
            sed -i '1s|^.*|#!/system/bin/sh|' "$NH_TERM/scripts/bootkali" 2>/dev/null
            chmod +x "$NH_TERM/scripts/bootkali"
        fi
        
        mkdir -p "$NH_TERM/usr/local/bin" 2>/dev/null
        
        for cmd in sh bash su; do
            if [ ! -L "$NH_TERM/usr/bin/$cmd" ]; then
                ln -sf /system/bin/sh "$NH_TERM/usr/bin/$cmd" 2>/dev/null
            fi
        done
    fi
}

fix_busybox() {
    echo "Fixing BusyBox symlinks..."
    
    NH_TERM="/data/data/com.offsec.nhterm/files"
    BUSYBOX_PATH="$NH_TERM/usr/bin/busybox"
    
    if [ -f "$BUSYBOX_PATH" ]; then
        chmod +x "$BUSYBOX_PATH"
        cd "$NH_TERM/usr/bin" 2>/dev/null || return
        
        for applet in $("$BUSYBOX_PATH" --list 2>/dev/null); do
            if [ ! -e "$applet" ]; then
                ln -sf busybox "$applet" 2>/dev/null
            fi
        done
    fi
}

main() {
    echo "========================================="
    echo "Kali NetHunter Fix Script"
    echo "========================================="
    
    if [ "$(id -u)" != "0" ]; then
        echo "Error: This script must be run as root"
        exit 1
    fi
    
    fix_nethunter_permissions
    fix_selinux_contexts
    fix_shell_scripts
    fix_busybox
    add_to_denylist
    
    echo "========================================="
    echo "NetHunter fixes applied successfully!"
    echo "Please reboot your device."
    echo "========================================="
}

if [ "$1" = "run" ]; then
    main
fi

