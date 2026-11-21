# Homes+ (Fabric 26.2 and 26.3)

Unofficial port of [**/sethome and /home Commands**](https://modrinth.com/mod/sethome-and-home-commands) to Minecraft **26.2** and **26.3** (Fabric), with multi-home features.

Source and issues: https://github.com/VashaTS/homesplus  
Modrinth: https://modrinth.com/mod/homesplus

The in-game and save-data mod id remains `sethome`, so existing homes and `config/sethome.json` keep working.

## Credit

- Original author: **CornDawgCoco** (Modrinth). Original project: https://modrinth.com/mod/sethome-and-home-commands
- Multi-home features (named homes, `/listhomes`, `/delhome`, and the config file): **VashaTS**
- License: **MIT**
- Upstream version: **1.0.0** for Minecraft **26.1.2** (Fabric)
- No public source repository was listed on Modrinth, so the 26.2 port was reconstructed from the published jar with Vineflower.

## Commands

- `/sethome` — save your current position, rotation, and dimension as your default home (internally named `home`). This still works exactly as in the original mod.
- `/sethome <name>` — save a separate named home. Overwriting an existing name is always allowed.
- `/home` — teleport to your default home, including across dimensions.
- `/home <name>` — teleport to a named home. Home names are tab-completed.
- `/delhome` — delete your default home.
- `/delhome <name>` — delete a named home. Home names are tab-completed.
- `/listhomes` — list your homes with name, world, and rounded coordinates. Click an entry to run `/home <name>`.
- `/listhomes <player>` — permission level 2 (ops) only. List another player's homes. Works for offline players if they have joined this server before (looked up from the user cache by name; homes are stored by UUID).
- `/homesconfig reload` — permission level 2 (ops) only. Reload `config/sethome.json`.

Home names may only contain letters, digits, underscores and hyphens, and can be at most 32 characters. Names are case-insensitive.

## Config

On first start the mod creates `config/sethome.json`:

```json
{
  "maxHomes": 0,
  "opsBypassLimit": false
}
```

- `maxHomes` — maximum homes per player, including the default home. **`0` or `-1` means unlimited** (the default is `0`). Setting a new home beyond the limit fails with a message; overwriting an existing name is always allowed.
- `opsBypassLimit` — if `true`, ops can ignore `maxHomes`. Default is `false`.

The config is loaded when the server starts. Use `/homesconfig reload` to apply changes without restarting.

## Requirements

- Minecraft 26.2 or 26.3
- Fabric Loader 0.19.5 or later
- Fabric API
- Java 25

## Build

Requires JDK 25.

```bash
./gradlew build
```

The jar is written to `build/libs/sethome-1.1.0.jar`.
