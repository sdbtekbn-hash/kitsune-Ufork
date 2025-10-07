# Kitsune Ufork Magisk

Kitsune Ufork is a fork of Magisk that brings back MagiskHide and adds SuList functionality to Magisk 29.0.

## Features

- Based on Magisk 29.0
- MagiskHide support
- SuList mode (whitelist mode for root access)
- All features from official Magisk 29.0

## What is MagiskHide?

MagiskHide allows you to hide Magisk modifications from specific apps. This is useful for apps that detect root access.

## What is SuList?

SuList is the opposite of MagiskHide. Instead of denying apps from seeing Magisk, it allows ONLY specific apps to access Magisk. All other apps will not see any Magisk modifications.

## Usage

### MagiskHide

1. Enable DenyList in settings
2. Go to DenyList configuration
3. Select apps you want to hide Magisk from

### SuList

1. Enable SuList mode using: `magisk --denylist sulist enable`
2. Reboot your device
3. Add apps to the list that should have access to Magisk
4. All other apps will not see Magisk

**Note**: SuList mode cannot be disabled without a reboot.

## Building

See [Building and Development](https://topjohnwu.github.io/Magisk/build.html)

## Credits

- [topjohnwu](https://github.com/topjohnwu) for Magisk
- [HuskyDG](https://github.com/HuskyDG) for Kitsune Magisk
- All contributors to the Magisk project

## License

This project is licensed under the GNU General Public License v3.0 or later (GPL-3.0-or-later).

See [LICENSE](LICENSE) for details.
