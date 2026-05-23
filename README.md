# TW Bot

A Discord moderation and community bot for the Trophy Wars server, built with Java 17, [JDA](https://github.com/discord-jda/JDA) 5, and SQLite. It provides slash commands for moderation, staff strikes and appeals, event name registration, managed voice channels, emoji tools, and several automated server features.

## Prerequisites

- **Java 17** or newer
- **Maven 3.6+**
- A **Discord bot application** with a bot token ([Discord Developer Portal](https://discord.com/developers/applications))

## Quick start

### 1. Create and configure the bot in Discord

1. Create an application in the [Discord Developer Portal](https://discord.com/developers/applications) and add a **Bot** user.
2. Copy the bot token (you will use it as `BOT_TOKEN`).
3. Under **Bot → Privileged Gateway Intents**, enable:
   - **Server Members Intent**
   - **Message Content Intent**
4. Invite the bot to your server with permissions for moderation, managing roles/channels, and using slash commands. At minimum, the bot typically needs:
   - Manage Server, Manage Roles, Manage Channels
   - Kick Members, Ban Members, Moderate Members (timeout)
   - Manage Messages, Read Message History
   - Manage Emojis and Stickers (for `/steal`)
   - Connect / Manage Channels (for voice features)

### 2. Configure environment variables

Create a `.env` file in the project directory (see [Where to put `.env`](#where-to-put-env)). The bot searches several locations automatically; placing `.env` inside `TW Bot/` is the simplest approach when developing locally.

**Required:**

| Variable | Description |
|----------|-------------|
| `BOT_TOKEN` | Discord bot token (also accepts `TOKEN` as an env var name) |

**Optional:**

| Variable | Default | Description |
|----------|---------|-------------|
| `GUILD_ID` | Hardcoded in `BotConfig` | Guild where slash commands are registered |
| `DATABASE_PATH` | `tw-bot.db` | SQLite database file path |
| `DATABASE_URL` | — | Full JDBC URL (overrides `DATABASE_PATH`), e.g. `jdbc:sqlite:/data/tw-bot.db` |
| `BOT_STATUS` | `🌍 Watching TW!` | Bot activity text |
| `BOT_STATUS_URL` | Twitch URL in config | Streaming activity URL |
| `BOT_ONLINE_STATUS` | `ONLINE` | One of: `ONLINE`, `IDLE`, `DO_NOT_DISTURB`, `INVISIBLE` |
| `ENV_FILE_PATH` | — | Custom path/filename for the env file |

Example `.env`:

```env
BOT_TOKEN=your_discord_bot_token_here
GUILD_ID=your_guild_id_here
DATABASE_PATH=tw-bot.db
```

> **Security:** Never commit `.env` or your bot token to version control.

### Where to put `.env`

The bot looks for `.env` in the current directory, parent directories, `TW Bot/`, home directory, and common container paths (`/app`, `/home/container`, etc.). You can also set `ENV_FILE_PATH` to point at a specific file.

### 3. Build and run

From the repository root:

```bash
cd "TW Bot"
mvn clean package
java -jar target/tw-bot-1.0.0.jar
```

Or run directly with Maven (no shaded JAR):

```bash
cd "TW Bot"
mvn compile exec:java -Dexec.mainClass="TWBot.TWBot"
```

On successful startup you should see:

- `Database connection successful!`
- `Successfully registered N guild slash commands for guild: ...`
- `TWBot started successfully!`

Stop the bot with `Ctrl+C`; a shutdown hook cleans up scheduled services.

## Using the bot

### Slash commands

Commands are registered **per guild** (using `GUILD_ID`). Type `/` in Discord to see available commands. For a full in-Discord reference, run:

```
/help
```

That opens an ephemeral embed listing commands by category. To verify the bot is online:

```
/test
```

### Command overview

| Category | Commands |
|----------|----------|
| **General** | `/help`, `/test` |
| **Event names** | `/eventname submit`, `/eventname check` (mod) |
| **Moderation** | `/warn`, `/mute`, `/unmute`, `/timeout`, `/untimeout`, `/kick`, `/ban`, `/unban`, `/purge`, `/reason`, `/role`, `/setmuterole`, `/restrict`, `/unrestrict`, `/restrict-setup`, `/steal` |
| **Strikes & appeals** | `/strike`, `/strikes`, `/removestrike`, `/clearstrikes`, `/editstrike`, `/appeal`, `/myappeals`, `/pendingappeals`, `/reviewappeal`, `/undoappeal` |
| **Voice** | `/vchelp`, `/setup`, `/createvoice`, `/deletevoice`, `/mychannels`, `/activechannels`, `/vcstats`, `/vcdbinfo` |
| **Admin** | `/dbinfo`, `/backupstrikes`, `/checkroles`, `/appealscanner`, `/rolerestoration`, demotion list commands (`/initdemotionlist`, `/adddemotion`, etc.), `/void-checker`, `/say`, `/cmdperm` |

Voice channel details are documented in Discord via `/vchelp`.

### Permissions

Most moderation commands check Discord roles defined in `BotConfig.java` (Server Ownership, Manager, Co-Manager, Overseer, Moderator, Staff, etc.). In general:

- **Moderator** — warn, mute, kick, ban, strikes, void-checker, etc.
- **Admin** — clear strikes, demotion list management, database backups, appeal scanner controls
- **Staff** — appeal review commands, reaction scan

Server Ownership and admin roles have broad access. `/cmdperm` lets server ownership configure per-role command access dynamically.

Discord's own permission checks still apply (for example, the bot cannot kick someone above its highest role).

### Automatic behavior

Without slash commands, the bot also:

- Welcomes new members and reacts in the welcome channel
- Re-applies mute roles when a muted user rejoins
- Adds configured reactions to messages in designated channels
- Enforces channel restrictions (links, attachments, etc.) where configured
- Logs messages and moderation actions to configured log channels
- Runs background services: ownership pings, mute expiry, appeal scanning, role restoration after temporary demotions

Many channel, role, and message IDs for these features are defined in `BotConfig.java` for the Trophy Wars server.

## Deploying to another server

This codebase is tailored to the Trophy Wars Discord server. To run it elsewhere you will typically need to:

1. Set `GUILD_ID` in `.env` to your server's ID.
2. Update role and channel IDs in `TW Bot/src/main/java/TWBot/config/BotConfig.java`.
3. Re-invite the bot with appropriate permissions and enabled intents.
4. Run `/setup` (voice) and `/restrict-setup` if you use those features.

## Project structure

```
Trophy Wars/
├── README.md
└── TW Bot/
    ├── pom.xml
    └── src/main/java/TWBot/
        ├── TWBot.java          # Entry point
        ├── config/              # BotConfig, env loading
        ├── commands/            # Slash command handlers
        ├── database/            # SQLite access
        ├── events/              # Discord event listeners
        ├── models/              # Data models
        ├── repositories/        # Event name storage
        ├── services/            # Business logic (strikes, mutes, voice, etc.)
        └── utils/               # Embeds, permissions, formatting
```

## Troubleshooting

| Issue | What to check |
|-------|----------------|
| `Bot token not found!` | Set `BOT_TOKEN` in `.env` or environment variables |
| `Failed to connect to database!` | Ensure the process can write to `DATABASE_PATH`; use an absolute path in production |
| `Could not find guild with ID ... Commands NOT registered!` | Bot must be in the guild; verify `GUILD_ID` |
| Slash commands missing | Wait a minute after startup; confirm guild ID; re-run the bot to re-register commands |
| Bot cannot moderate users | Bot role must be above target roles; grant Kick/Ban/Moderate Members permissions |

## Tech stack

- Java 17
- JDA 5.1.1
- SQLite (via sqlite-jdbc)
- dotenv-java for configuration
- Logback / SLF4J for logging

## License

No license file is included in this repository. Add one if you plan to distribute or open-source the project.
