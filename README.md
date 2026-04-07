# 🟢 WAKGCraft — The DiscordSRV of WhatsApp!

**WAKGCraft** is a PaperMC plugin that bridges your Minecraft server with WhatsApp via [WA-AKG](https://wa-akg.aikeigroup.net/). Send in-game events to WhatsApp, receive WhatsApp messages in-game, and manage your server remotely — all from WhatsApp!

> **Compatible with:** PaperMC 1.21+ | Java 21+ | PlaceholderAPI (Optional)

---

## 📦 Installation

1. **Download** the latest `WAKGCraft-x.x.x.jar` from the [releases](#) page.
2. **Place** the `.jar` file into your server's `plugins/` folder.
3. **Start** (or restart) the server. The plugin will generate default config files.
4. **Stop** the server, then edit the config files (see [Configuration](#%EF%B8%8F-configuration) below).
5. **Start** the server again. You're all set!

### Requirements

| Requirement | Version | Notes |
|---|---|---|
| PaperMC | 1.21+ | Spigot is **not** supported |
| Java | 21+ | Required by PaperMC 1.21 |
| WA-AKG Account | — | Register at [wa-akg.aikeigroup.net](https://wa-akg.aikeigroup.net/) |
| PlaceholderAPI | 2.11.6+ | **Optional** — enables `%placeholder%` support |

---

## ⚙️ Configuration

All config files are located in `plugins/WAKGCraft/`.

### `config.yml` — Main Configuration

#### 🔑 WA-AKG API Connection

```yaml
wa-akg:
  api-url: "https://wa-akg.aikeigroup.net/api"
  api-key: "YOUR_API_KEY_HERE"    # Get from WA-AKG Dashboard -> Sessions -> API Keys
  session-id: "session-01"        # Your WA-AKG Session ID
```

| Key | Description |
|---|---|
| `api-url` | WA-AKG API Base URL. Don't change unless self-hosting. |
| `api-key` | Your personal API Key from WA-AKG Dashboard. |
| `session-id` | The session name you created in WA-AKG. |

#### 📡 Channels

Channels are named WhatsApp destinations. You can add as many as you want — custom commands reference these names.

```yaml
Channels:
  GlobalChat:
    jid: "120363xxxxxx@g.us"
    send-to-wa: true
    read-from-wa: true
  AdminAlerts:
    jid: "6281xxxxxx@s.whatsapp.net"
    send-to-wa: true
    read-from-wa: false
```

| Channel | Description |
|---|---|
| `GlobalChat` | Two-way sync channel. In-game chat ⇆ WhatsApp. Also used for server lifecycle and player events. |
| `AdminAlerts` | Receives player reports via `/wakg report`. |
| *(Custom)* | Add any name you like. Use it in `commands.yml` via `target-channels` or `channels`. |

> 💡 All channels listed here automatically become **two-way chat sync** channels.

**How to find your JID:**

| Type | Format | Example |
|---|---|---|
| WhatsApp Group | `<group-id>@g.us` | `120363401473125769@g.us` |
| Personal Number | `<country-code><number>@s.whatsapp.net` | `6281234567890@s.whatsapp.net` |

> 💡 You can find Group JIDs on your **WA-AKG Dashboard → Chats**.

---

### 📤 Minecraft → WhatsApp Events

All events are sent to the `Channels.GlobalChat` JID.

```yaml
minecraft-to-whatsapp:
  server-start:
    enabled: true
    format: "🚀 *Server Started!*%nl%The Minecraft server is now online."
  server-stop:
    enabled: true
    format: "🛑 *Server Stopped!*%nl%The Minecraft server has gone offline."
  chat:
    enabled: true
    format: "💬 *[%player_name%]*: %message%"
  player-join:
    enabled: true
    format: "🟢 *%player_name%* has joined the server!"
  player-quit:
    enabled: true
    format: "🔴 *%player_name%* has left the server."
  player-death:
    enabled: true
    format: "💀 *%player_name%* just died: %death_message%"
  advancement:
    enabled: true
    format: "🏆 *%player_name%* has made the advancement [%advancement%]!"
```

#### Event Placeholders

| Placeholder | Available In | Description |
|---|---|---|
| `%player_name%` | chat, join, quit, death, advancement | The player's Minecraft name |
| `%message%` | chat | The chat message content |
| `%death_message%` | death | The death message (e.g., "was slain by Zombie") |
| `%advancement%` | advancement | The advancement title |
| `%nl%` | **ALL** formats | Inserts a **new line** in the WhatsApp message |
| `%newline%` | **ALL** formats | Same as `%nl%` |
| `%player_xxx%` | player events | Any PlaceholderAPI placeholder (requires PlaceholderAPI) |

#### WhatsApp Text Formatting

| Syntax | Result |
|---|---|
| `*bold*` | **bold** |
| `_italic_` | _italic_ |
| `~strikethrough~` | ~~strikethrough~~ |
| `` ```monospace``` `` | `monospace` |

---

### 📥 WhatsApp → Minecraft (Webhook)

#### Webhook Setup

```yaml
whatsapp-to-minecraft:
  webhook:
    enabled: true
    port: 8080
    debug: false
```

**How to connect:**

1. Make sure the `port` (default `8080`) is **port-forwarded** or accessible.
2. Go to **WA-AKG Dashboard → Sessions → Webhooks**.
3. Add URL: `http://YOUR_SERVER_IP:8080/webhook`

  # Format for messages arriving from WhatsApp into Minecraft.

#### Built-In Commands Configuration

You can customize the aliases, enable/disable, and fully modify the structural formatting of built-in commands:

```yaml
whatsapp-to-minecraft:
  built-in-commands:
    list:
      enabled: true
      command: "list"
      format:
        header: "👥 *Online Players (%online_count%/%max_players%)*%nl%"
        player-item: "- %player_name%%nl%"
        empty: "No players online"
    # ... format strings for status, whitelist, execute, and help
```

#### Chat Format (WA → MC)

```yaml
  chat-format: "&7[&a%channel%&7] &f%wa_name%&8: &7%message%"
```

| Placeholder | Description |
|---|---|
| `%wa_name%` | The sender's WhatsApp display name |
| `%message%` | The message content |
| `%channel%` | The channel name from `config.yml` (e.g., `GlobalChat`, `AdminAlerts`) |

---

## 📋 Custom Commands (`commands.yml`)

The `commands.yml` file lets you create custom commands for **both** sides.

### Argument Placeholders

| Placeholder | Description |
|---|---|
| `%args%` | **ALL** text after the command. If used with positional args, captures everything AFTER the last positional arg. |
| `%args-1%` | The **1st** word after the command |
| `%args-2%` | The **2nd** word after the command |
| `%args-N%` | The **Nth** word after the command |

> ⚠️ **Rule:** `%args%` must always come **after** positional args (`%args-N%`), never before!

### How Args Work — Examples

```
Command input: /reportwa Steve he was griefing in spawn

Format: "Target: %args-1%, Reason: %args%"
Result: "Target: Steve, Reason: he was griefing in spawn"
```

```
Command input: !tp Steve Alex

Format: "Teleporting %args-1% to %args-2%"
Result: "Teleporting Steve to Alex"
```

```
Command input: /wagive Steve diamond 64

Format: "give %args-1% %args-2% %args-3%"
   %args-1% = Steve
   %args-2% = diamond
   %args-3% = 64
```

### ⚠️ Usage Error Messages

If a player/user doesn't provide enough arguments, WAKGCraft will automatically show a usage error:

```
❌ Wrong usage!
Description: Report a player to admins
Usage: /reportwa <player> <reason>
Example: /reportwa Steve griefing in spawn
```

This behavior is driven by the `usage`, `description`, and `example` fields in `commands.yml`.

### Minecraft Commands (In-Game → WhatsApp)

Commands are **auto-registered** to Minecraft at server start — no `plugin.yml` editing needed!

```yaml
minecraft-commands:
  reportwa:
    command: "reportwa"                    # Players type /reportwa
    target-channels:                       # Send to these channels (from config.yml)
      - "AdminAlerts"
    format: "🚨 *REPORT*%nl%Reporter: *%player_name%*%nl%Suspect: *%args-1%*%nl%Reason: %args%"
    permission: ""                          # Leave "" for no restriction
    description: "Report a player"          # Shown in /wakg help
    usage: "<player> <reason>"             # Shown in help & error messages
    example: "Steve griefing in spawn"     # Shown when wrong args
    response-message: "&aReport sent!"     # In-game feedback
    error-message: ""                       # Custom error (optional, auto-generated if empty)
```

**Channel Routing Options:**

```yaml
# Send to ONE channel
target-channels:
  - "AdminAlerts"

# Send to MULTIPLE channels
target-channels:
  - "GlobalChat"
  - "AdminAlerts"

# Send to ALL channels
target-channels:
  - "global"
```

| Field | Required | Description |
|---|---|---|
| `command` | ✅ | Command name (without `/`) |
| `target-channels` | ✅ | List of channel names from `config.yml`. Use `["global"]` for all channels. |
| `format` | ✅ | Message format. Supports `%player_name%`, `%args%`, `%args-N%`, `%nl%`, PlaceholderAPI |
| `execute-console` | ❌ | Console command to run. Supports `%args%`, `%args-N%` |
| `permission` | ❌ | Bukkit permission. `""` = no restriction |
| `description` | ❌ | Short description for `/wakg help` |
| `usage` | ❌ | Arg structure hint (e.g., `<player> <reason>`) |
| `example` | ❌ | Example args for error messages |
| `response-message` | ❌ | In-game feedback (supports `&` colors) |
| `error-message` | ❌ | Custom error message (auto-generated if empty) |

### WhatsApp Commands (WhatsApp → Minecraft)

```yaml
whatsapp-commands:
  giveapple:
    command: "giveapple"                    # Users type !giveapple in WhatsApp
    channels:                               # Only respond from these channels
      - "GlobalChat"
    admin-only: true
    description: "Give apples to a player"
    usage: "<player>"
    example: "Steve"
    execute-console: "give %args-1% apple 64"
    reply: "🍎 Giving 64 apples to *%args-1%*."
```

**Channel Listening Options:**

```yaml
# Only respond from specific channel(s)
channels:
  - "GlobalChat"

# Respond from ALL configured channels
channels:
  - "global"

# Respond from ANY source (including DMs) — leave empty
channels: []
```

| Field | Required | Description |
|---|---|---|
| `command` | ✅ | Trigger word (without prefix) |
| `channels` | ❌ | List of channel names to listen from. `["global"]` = all channels, `[]` = any source. |
| `admin-only` | ❌ | `true` = only admin JIDs can use |
| `reply` | ❌ | Reply sent back to WhatsApp. Supports `%args%`, `%args-N%` |
| `execute-console` | ❌ | Console command to run. Supports `%args%`, `%args-N%` |
| `description` | ❌ | Short description for `!help` |
| `usage` | ❌ | Arg structure hint |
| `example` | ❌ | Example args for error messages |

---

## 🎮 In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/wakg help` | `wakgcraft.admin` | Show all available commands (permission-filtered) |
| `/wakg reload` | `wakgcraft.admin` | Reload all configurations |
| `/wakg send <jid> <message>` | `wakgcraft.admin` | Send a message to WhatsApp |
| `/wakg report <player> <reason>` | `wakgcraft.report` | Report a player (sent to AdminAlerts) |
| Custom commands from `commands.yml` | Configurable | Auto-registered at startup |

### Help Command

The `/wakg help` command shows a **permission-filtered** list:
- **Admins** (`wakgcraft.admin`) see **all** commands
- **Regular players** see only commands they have permission for
- Custom commands without a permission are visible to everyone

---

## 📱 WhatsApp Commands (Built-in)

| Command | Admin Only | Description |
|---|---|---|
| `!list` | ❌ | View online players |
| `!status` | ❌ | View server TPS, RAM, and player count |
| `!help` | ❌ | Show available commands (permission-filtered) |
| `!whitelist add/remove <name>` | ✅ | Manage server whitelist |
| `!execute <command>` | ✅ | Run any server console command |
| Custom commands from `commands.yml` | Configurable | Your custom commands |

### Help Command (WhatsApp)

The `!help` command shows a **permission-filtered** list:
- **Admin JIDs** see **all** commands (built-in + admin + custom)
- **Non-admins** see only non-admin commands

---

## 🔌 PlaceholderAPI Support

When [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed, you can use **any** PAPI placeholder in format strings.

```yaml
format: "💬 *[%player_name%]* (❤️ %player_health%) from %player_world%: %message%"
```

> ⚠️ Server-start/stop formats only support non-player placeholders (e.g., `%server_online%`).

---

## 🐛 Debugging / Troubleshooting

### Messages not appearing in Minecraft?

1. Enable webhook debug: set `whatsapp-to-minecraft.webhook.debug: true`
2. Send a message from WhatsApp
3. Check console for `[Webhook Debug] Received Payload: {...}`
   - ✅ Payload visible → Check `GlobalChat` JID matches `remoteJid`
   - ❌ Nothing → Check port forwarding and webhook URL

### Messages not appearing in WhatsApp?

- Ensure `api-key` is set (not `YOUR_API_KEY_HERE`)
- Ensure `session-id` matches your WA-AKG session
- Ensure `Channels.GlobalChat` has a valid JID
- Check console for API errors

### Custom commands showing "Unknown command"?

- Make sure `commands.yml` has the command under `minecraft-commands`
- Restart the server after editing `commands.yml` (commands are registered at startup)
- Check console for `Registered custom command: /commandname`

---

## 🏗️ Building from Source

```bash
cd WAKGCraft
mvn clean package
```

Output: `target/WAKGCraft-x.x.x.jar`

---

## 📝 Permissions

| Permission | Default | Description |
|---|---|---|
| `wakgcraft.admin` | OP | Access to `/wakg` admin commands |
| `wakgcraft.report` | Everyone | Access to `/wakg report` |

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 💬 Support

- **WA-AKG Dashboard**: [wa-akg.aikeigroup.net](https://wa-akg.aikeigroup.net/)
- **GitHub Issues**: [github.com/mrifqidaffaaditya/WAKGCraft/issues](https://github.com/mrifqidaffaaditya/WAKGCraft/issues)
