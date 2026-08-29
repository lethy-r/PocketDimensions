# Pocket Dimensions 0.0.7-ALPHA
Pocket Dimensions lets every player own a personal world — invite friends, set trust levels, teleport mobs, and customize generation. One jar supports **Minecraft 1.19.4 → 26.2** (Bukkit, Spigot, Paper, Purpur).

### Commands
| Command | Usage | Permission | Description |
| :--- | :--- | :--- | :--- |
| **/givepd** | `/givepd <player> [dimension\|mobtool]` | `pocketdimensions.commands.givepd` | Gives the PD item bound to `<player>` (the item only works for them). `mobtool` requires `pocketdimensions.commands.givepd.tool`. |
| **/pdtp** | `/pdtp <player>` | `pocketdimensions.commands.pdtp` | Admin teleport into any player's dimension (offline supported). |
| **/pd** | `/pd <action> [args]` | `pocketdimensions.commands.players.pd` | Base command; every action below has its own permission. |
| | `/pd invite <player>` | `pocketdimensions.commands.player.pd.invite` | Invite a player to the dimension you are standing in (expires in 120s). Works in someone else's dimension only if you are **trusted** there. |
| | `/pd acceptinv [player]` | `pocketdimensions.commands.player.pd.invite` | Accept a pending invite; with several invites, pick the sender. |
| | `/pd kick <player>` | `pocketdimensions.commands.player.pd.kick` | Kick a player from your dimension. |
| | `/pd trust <player> [builder\|trusted]` | `pocketdimensions.commands.player.pd.trust` | Grant trust in your dimension. Default tier: builder. `trusted` also needs `...pd.trust.trusted`. |
| | `/pd untrust <player>` | `pocketdimensions.commands.player.pd.trust` | Revoke trust. |
| | `/pd setspawn` | `pocketdimensions.commands.player.pd.setspawn` | Set your dimension's spawn point to where you stand. |
| | `/pd upgrade` | `pocketdimensions.commands.player.pd.upgrade` | Upgrade your border to the next configured tier (charges money when the economy is enabled). |
| | `/pd settings [preset\|environment\|notify] [value]` | `pocketdimensions.commands.player.pd.settings` | View/change your dimension preset, environment and entry notifications. Applies when your dimension is next created. |
| | `/pd menu` | `pocketdimensions.commands.player.pd.menu` | Open the GUI menu. |
| **/pdleave** | `/pdleave` | `pocketdimensions.commands.player.pdleave` | Leave the current pocket dimension. |
| **/pdgamerule** | `/pdgamerule <player> <rule> [value]` | `pocketdimensions.commands.pdgamerule` | View or set a gamerule in a player's dimension — **persisted per dimension** and re-applied on every entry. |
| **/pdworldborder** | `/pdworldborder <player> <size>` | `pocketdimensions.commands.pdworldborder` | Set a player's border size directly. |
| **/pdreload** | `/pdreload` | `pocketdimensions.commands.reload` | Reload config, messages, mob-teleport settings, protection extras and the economy hook. Storage type changes need a restart. |
| **/pdlist** | `/pdlist` | `pocketdimensions.commands.admin.list` | List all dimensions: owner, loaded state, border, last seen. |
| **/pdadmin** | see below | `pocketdimensions.commands.admin.*` | `/pdadmin delete <player> confirm` (world **and** all data), `/pdadmin reset <player> confirm` (world only, data kept), `/pdadmin cleanup <days> confirm` (remove dimensions of players inactive for N days). Refuses while players are inside. |

### GUI — /pd menu
A two-view inventory. Sections are individually permission-gated:
- **Travel** (`...pd.menu.teleport`) — enter or leave your dimension.
- **Trusted players** (`...pd.menu.trust`) — one skull per trusted player; left-click cycles their tier (builder ↔ trusted), right-click removes. Adding new players: `/pd trust <player>`.
- **Preset / Environment / Notifications** (`...pd.menu.settings`) — cycle FLAT → VOID → NORMAL and NORMAL → NETHER → THE_END (applies on next creation) and toggle entry notifications.
- **World border** (`...pd.menu.border`) — shows the current and next tier size; click to upgrade.

### Trust & protection
Dimension owners grant trust tiers. Everyone else (even past invitees) is a visitor:
- **Visitor** — can look around, use doors/buttons, but cannot build, break, open containers or hurt the owner's mobs.
- **Builder** — can build, break and use containers.
- **Trusted** — builder rights **plus** can invite other players into the dimension.
Admins (and anyone with `pocketdimensions.bypass.protection`) bypass all of it. Explosions never damage blocks inside dimensions (configurable).

### Dimension settings & presets
New dimensions are generated from `dimension.default-preset` (FLAT / VOID / NORMAL) and `dimension.default-environment` (NORMAL / NETHER / THE_END). Players can pick their own for their next creation via `/pd settings` or the menu. Existing worlds are never regenerated. `/pd setspawn` moves the in-dimension spawn; invited players and first entries arrive there.

### Storage
Pick the backend in `config.yml` (`storage.type`):
- **JSON** (default) — `lastlocs.json`, `pdborders.json`, `dimensions.json` in the plugin folder; atomic, serialized writes. The first two are the same files 0.0.5 used.
- **SQLITE / MYSQL** — bundled JDBC drivers, nothing to install. Switching auto-imports existing JSON data once and renames the old files `*.imported`. Tables created on first boot: `pd_locations`, `pd_borders`, `pd_meta`, `pd_trusts`, `pd_settings`.

### Economy (Vault) — disabled by default
1. Install **Vault** and any economy plugin (EssentialsX, CMI…).
2. Set `economy.enabled: true` in `config.yml` and set prices.
3. `economy.creation-cost` is charged the first time a player's world is created; `economy.upgrades` are the border tiers used by `/pd upgrade` and the menu.
Without Vault (or with `economy.enabled: false`) everything works but is **free**.

### World Management
Empty dimensions unload (after saving) after `world-management.unload-delay-seconds`; players who log out inside are returned on rejoin. `entity-cap` warns owners whose dimension exceeds the limit (0 = off).

### Mob Teleportation
Right-click a mob with your key item (or the mob teleporter tool) to store it in your dimension or retrieve it back.
- `pocketdimensions.mobteleport.use` — use the feature at all.
- `pocketdimensions.mobteleport.store` — send mobs **in**.
- `pocketdimensions.mobteleport.retrieve` — take mobs **out**.

### Default Game Rules
Defaults live in `config.yml` (`default-gamerules:`); rule names are resolved at runtime with legacy fallbacks, so both 26.x and pre-1.21.11 spellings work on any server. Per-dimension overrides made with `/pdgamerule` are stored and win over the defaults.

---

### Permission Reference
| Permission | What it controls | Default |
| :--- | :--- | :--- |
| `pocketdimensions.receive.key` | Getting the key item on first join (`key-item.give-on-first-join`) | `true` |
| `pocketdimensions.feedback` | Entry/exit titles, sounds and entry notifications | `true` |
| `pocketdimensions.bypass.protection` | Ignores all dimension protection | `op` |
| `pocketdimensions.commands.givepd` | `/givepd` | `op` |
| `pocketdimensions.commands.givepd.tool` | Giving the `mobtool` item | `op` |
| `pocketdimensions.commands.pdtp` | `/pdtp` admin teleport | `op` |
| `pocketdimensions.commands.players.pd` | Base `/pd` command | `op` |
| `pocketdimensions.commands.player.pd.invite` | `/pd invite`, `/pd acceptinv` | `op` |
| `pocketdimensions.commands.player.pd.kick` | `/pd kick` | `op` |
| `pocketdimensions.commands.player.pd.kick.bypass` | Cannot be kicked | `op` |
| `pocketdimensions.commands.player.pd.trust` | `/pd trust`, `/pd untrust` | `op` |
| `pocketdimensions.commands.player.pd.trust.trusted` | Granting the full `trusted` tier | `op` |
| `pocketdimensions.commands.player.pd.setspawn` | `/pd setspawn` | `op` |
| `pocketdimensions.commands.player.pd.upgrade` | `/pd upgrade` + menu border item | `op` |
| `pocketdimensions.commands.player.pd.settings` | `/pd settings` + menu setting items | `op` |
| `pocketdimensions.commands.player.pd.menu` | Opening `/pd menu` | `op` |
| `pocketdimensions.commands.player.pd.menu.teleport` | Menu travel item | `op` |
| `pocketdimensions.commands.player.pd.menu.trust` | Menu trust section | `op` |
| `pocketdimensions.commands.player.pd.menu.settings` | Menu preset/environment/notification items | `op` |
| `pocketdimensions.commands.player.pd.menu.border` | Menu border item | `op` |
| `pocketdimensions.commands.player.pdleave` | `/pdleave` | `op` |
| `pocketdimensions.commands.pdgamerule` | `/pdgamerule` (persists per dimension) | `op` |
| `pocketdimensions.commands.pdworldborder` | `/pdworldborder` | `op` |
| `pocketdimensions.commands.reload` | `/pdreload` | `op` |
| `pocketdimensions.commands.admin.list` | `/pdlist` | `op` |
| `pocketdimensions.commands.admin.delete` | `/pdadmin delete` | `op` |
| `pocketdimensions.commands.admin.reset` | `/pdadmin reset` | `op` |
| `pocketdimensions.commands.admin.cleanup` | `/pdadmin cleanup` | `op` |
| `pocketdimensions.mobteleport.use` | Use mob teleportation | `op` |
| `pocketdimensions.mobteleport.store` | Send mobs into your dimension | `op` |
| `pocketdimensions.mobteleport.retrieve` | Take mobs out of your dimension | `op` |

**Recommended player rank** (regular players):
```yaml
- pocketdimensions.commands.players.pd
- pocketdimensions.commands.player.pd.invite
- pocketdimensions.commands.player.pd.kick
- pocketdimensions.commands.player.pd.trust
- pocketdimensions.commands.player.pd.setspawn
- pocketdimensions.commands.player.pd.upgrade
- pocketdimensions.commands.player.pd.settings
- pocketdimensions.commands.player.pd.menu
- pocketdimensions.commands.player.pdleave
- pocketdimensions.mobteleport.use
- pocketdimensions.mobteleport.store
- pocketdimensions.mobteleport.retrieve
```
**Trusted/co-op rank** (owners of shared dimensions): everything above **plus** `pocketdimensions.commands.player.pd.trust.trusted`.
To disable a feature for one player, negate their node (e.g. `-pocketdimensions.commands.player.pd.upgrade` in LuckPerms); to disable globally, set the matching `features.*` switch to `false`.

### Config Reference
| Key | Type / Default | Effect |
| :--- | :--- | :--- |
| `default-world-border-size` | int / `10000` | Border (diameter) when a player has no custom size. |
| `features.trust-protection` | bool / `true` | Global switch for the trust system and protection listener. |
| `features.dimension-presets` | bool / `true` | Allows players to choose preset/environment. |
| `features.setspawn` | bool / `true` | Allows `/pd setspawn`. |
| `features.gui-menu` | bool / `true` | Allows `/pd menu`. |
| `features.feedback` | bool / `true` | Titles, sounds and entry notifications. |
| `features.placeholderapi` | bool / `true` | Registers placeholders when PlaceholderAPI is installed. |
| `features.admin-tools` | bool / `true` | Reserved switch for the admin commands (they are permission-gated). |
| `storage.type` | `JSON`/`SQLITE`/`MYSQL` | Active backend; switching auto-imports JSON data. |
| `storage.sqlite.file` | string / `database.db` | SQLite file inside the plugin folder. |
| `storage.mysql.host/port/database/username/password/use-ssl/table-prefix` | — | MySQL connection and table prefix. |
| `dimension.default-preset` | `FLAT`/`VOID`/`NORMAL` | Generation for new dimensions. VOID = empty world with a small platform; NORMAL = terrain with a random seed. |
| `dimension.default-environment` | `NORMAL`/`NETHER`/`THE_END` | Environment for new dimensions. |
| `protection.protect-mobs` | bool / `true` | Visitors cannot hurt the owner's mobs. |
| `protection.block-explosions` | bool / `true` | Explosions deal no block damage inside dimensions. |
| `protection.extra-containers` | list / `[]` | Extra materials treated as locked containers. |
| `economy.enabled` | bool / `false` | Master switch for Vault charges. |
| `economy.creation-cost` | number / `0.0` | Charged on first dimension creation. |
| `economy.upgrades` | list of `{size, cost}` | Border tiers for `/pd upgrade` (free when economy is off). |
| `feedback.titles` | bool / `true` | Entry/exit titles. |
| `feedback.sounds` | bool / `true` | Entry/exit sounds. |
| `feedback.sound-enter` / `feedback.sound-leave` | sound name | Played on entry/exit. |
| `key-item.material/name/lore` | — | The key item's look. `%player%` is replaced. |
| `key-item.custom-model-data` | int / `0` | Resource-pack model (0 = off). |
| `key-item.glow` | bool / `false` | Enchant glint. |
| `key-item.give-on-first-join` | bool / `false` | Auto-give once per player (needs `pocketdimensions.receive.key`). |
| `key-item.interact-cooldown-ms` | int / `1000` | Anti-double-click cooldown for the item. |
| `key-item.craftable` + `recipe.shape` + `recipe.ingredients` | — | Optional crafting recipe; the crafted key binds to the crafter. |
| `tools.mob-teleporter.*` | — | Mob tool: `required`, material, name, lore, `custom-model-data`, `glow`. |
| `mob-teleport.enabled/mode/entities/exclude.categories/allow-named-mobs` | — | Mob teleport rules. |
| `default-gamerules.<rule>` | bool/int | Rules applied to every dimension; per-dimension `/pdgamerule` overrides win. |
| `world-management.auto-unload` | bool / `true` | Unload empty dimensions. |
| `world-management.unload-delay-seconds` | int / `300` | Grace period before an empty dimension unloads. |
| `world-management.sweep-interval-seconds` | int / `300` | Sweep frequency (min 30). |
| `world-management.entity-cap` | int / `0` | Warn owners above this entity count (0 = off). |

### PlaceholderAPI
`%pd_in_pd%`, `%pd_border%`, `%pd_visitors%`, `%pd_dimension_loaded%`.

### Developer API
Cancellable Bukkit events other plugins can listen to: `PocketDimensionCreateEvent`, `PlayerEnterDimensionEvent`, `PlayerLeaveDimensionEvent`, `InviteSendEvent`, `MobTeleportEvent` (package `mc.lethargos.pocketdimensions.api.event`).

---
**NOTE: This is one of my first plugins and it is still under heavy development. There are definitely bugs. Please report them on my discord - [https://discord.gg/H5XdXvb2pP](https://discord.gg/H5XdXvb2pP)** or add them to [https://github.com/lethy-r/PocketDimensions-Issues/issues](https://github.com/lethy-r/PocketDimensions-Issues/issues)
