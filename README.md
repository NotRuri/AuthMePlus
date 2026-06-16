# AuthMePlus

**AuthMePlus** is a Bukkit/Spigot plugin designed to streamline authentication for premium Minecraft players by allowing automatic login based on trusted IP addresses.

It integrates with **AuthMe Reloaded** and links player accounts to their IPs.

## Features

- **Automatic Login**
  - Instantly logs in players if they connect from a trusted IP.

- **Premium Account Verification**
  - Checks usernames against Mojang API to ensure they are premium accounts.

- **Async Operations**
  - Network requests and file saves are handled asynchronously to avoid server lag.

- **Fully Configurable Messages**
  - Customize all plugin messages via config.

## Requirements

- Java 25
- Paper / Folia server
- **AuthMe Reloaded** (recommended for full functionality)

## How It Works

1. When a player joins:
   - The plugin checks if their IP is already linked.
   - If yes → automatic login via AuthMe.

2. If not linked:
   - The plugin verifies if the account is **premium**.
   - Prompts the player to link their IP.

3. Once linked:
   - Future logins from that IP are automatic.

## Commands

| Command               | Description                             |
| --------------------- | --------------------------------------- |
| `/premium accept`     | Link your current IP to your account    |
| `/premium revoke`     | Remove your current IP                  |
| `/premium revoke all` | Remove all linked IPs                   |
| `/premium list`       | Show linked IPs (confirmation required) |
| `/premium about`      | Show plugin info                        |

## Configuration Files

### `config.yml`

- Enable/disable plugin
- Customize messages
- Control join behavior

### `linked.yml`

- Stores linked IPs per player
- Automatically managed by the plugin

## Compatibility

The plugin dynamically detects AuthMe API using:

- `fr.xephi.authme.api.v3.AuthMeApi`
- `fr.xephi.authme.api.API`
- Legacy `AuthMe` API

Tested target:

- Minecraft 26.1.2
- Java 25
- Paper/Folia

## External API

- Uses Mojang endpoint: https://api.mojang.com/users/profiles/minecraft/{username} to verify premium accounts.

## Notes

- Requires AuthMe to force login players.
- Without AuthMe, the plugin still runs but cannot auto-login users.
- IP-based login assumes stable player IPs.
