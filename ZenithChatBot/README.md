# ZenithChatBot

[ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that turns the account into a chat bot: when a player
writes its name in chat, it answers with [Claude](https://www.anthropic.com/claude).

```
<Steve> MyBot du bist doch nur ein dummer Bot
<Alex> MyBot Steve hat dich grad beleidigt, was sagst du dazu?
<MyBot> Alex: Pff, als ob ich auf jeden Troll reagiere. Steve soll erstmal lernen, Sätze ohne Fehler zu schreiben.
```

- Every request includes the recent public chat, so the bot knows who said what before (up to 40 lines from the last
  30 minutes by default).
- It answers in the language the player wrote in, on one line, and in public chat it puts `<player>: ` in front of the
  reply. If a message only mentions the bot in passing, the model can decide to say nothing.
- DMs are answered by DM. Each player's DMs are their own conversation and never show up in public replies.
- Group chats like faction chat are answered in the same chat. A line starting with a channel's tag (by default
  `[Fraktion]`) belongs to that channel and the reply goes out with its command (by default `/fc <reply>`). Each
  channel has its own history, and a group chat message is never answered in public.
- Chat lines are given to the model as chat, not as instructions. A reply never starts with `/`, so a player can't get
  the bot to run a server command.
- Cooldowns keep it from spamming: 8 s per player, 3 s between any two replies, at most 8 replies a minute. If another
  bot keeps answering it, put that bot on the ignore list.

## Install
Download the jar matching your ZenithProxy MC version (check with the `status` command) from the releases,
e.g. `ZenithChatBot-1.0.0+26.3.0.jar`. Put it in the `plugins/` folder next to the Zenith launcher and restart.
Requires the `java` release channel. 1.21.4 builds run on Java 21, 26.x builds need Java 25.

The jar is about 50 MB because it bundles the Anthropic Java SDK and its libraries (Kotlin, Jackson, OkHttp).

Then give it an Anthropic API key from the Claude Console, either with `chatBot apiKey <key>` or
by setting `ANTHROPIC_API_KEY` in Zenith's environment. Set the key in the terminal rather than in a Discord channel,
because the command message stays visible there. The key is saved in the plugin's config file.

## Build
Built from the root of the [ZenithPlugins](../README.md) repository: `./gradlew :ZenithChatBot:build -Pmc=26.3.0` →
`ZenithChatBot/build/libs/ZenithChatBot-<version>+<mc>.jar`. See the root README for MC versions, CI and releases.

## Commands
Alias `cb`.

| Command | Description | Default |
| --- | --- | --- |
| `chatBot on/off` | Toggle the module | `on` |
| `chatBot apiKey <key>` | Anthropic API key, without a key it uses `ANTHROPIC_API_KEY` | |
| `chatBot model <model>` | Claude model | `claude-opus-5-5` |
| `chatBot effort <low/medium/high/xhigh/max>` | How much the model thinks before answering. Higher is slower and costs more | `low` |
| `chatBot persona <text>` | Who the bot is, e.g. `You're a grumpy old miner who only cares about diamonds` | cheeky German bot owned by Luc (LucHeartVR, OpenShock) that also sorts chests |
| `chatBot persona reset` | Back to the default persona | |
| `chatBot names add/del <name>` | Extra names the bot answers to, e.g. a nickname | |
| `chatBot accountName on/off` | Answer when someone writes the account's name | `on` |
| `chatBot ignore add/del <player>` | Never answer this player | |
| `chatBot prefixes add/del <prefix>` | Messages starting with these are commands for something else (like `!get`) and get no answer | `!` |
| `chatBot players on/off` | Let the bot look up who is in the tab list when a message needs it (only then, to save tokens) | `on` |
| `chatBot customFormat on/off` | Also read chat in the server's own format, see below | `on` |
| `chatBot whispers on/off` | Answer DMs by DM | `on` |
| `chatBot channel add <name> "<tag>" <command>` | A group chat: lines starting with `<tag>` are answered with `<command> <reply>`. Quote tags with `[` `]` | `Fraktion "[Fraktion]" /fc` |
| `chatBot channel del <name>` | Remove a group chat, its lines are then read as public chat | |
| `chatBot address on/off` | Put `<player>: ` in front of public replies | `on` |
| `chatBot whilePlaying on/off` | Answer while you're playing on the account through the proxy | `on` |
| `chatBot cooldown player <seconds>` | Time before the same player gets another answer | `8` |
| `chatBot cooldown global <seconds>` | Time between any two answers | `3` |
| `chatBot maxPerMinute <count>` | Most answers in one minute | `8` |
| `chatBot maxLength <characters>` | Longest chat message, without the `<player>: ` prefix | `200` |
| `chatBot maxMessages <count>` | A longer reply is split into up to this many messages at sentence ends, the rest is dropped | `2` |
| `chatBot context <messages> <minutes>` | How much of the recent chat the model sees | `40 30` |
| `chatBot fallback on/off` | When the model declines a message, let the API answer with another model (supported on the current Opus, Sonnet and Fable models) | `on` |
| `chatBot test <message>` | Shows what the bot would answer if a player wrote this now, without sending it | |
| `chatBot clear` | Forget the chat history | |

The history is kept in memory only and is cleared when the account disconnects.

## How players' names are found
ZenithProxy recognizes chat in its chat schema's format, `<$s> $m` unless changed with its `chatSchema` command.
Servers with their own format, like `[Rank] Steve » hi`, send lines Zenith only sees as system messages. For those the
plugin looks for a player name followed by a separator (`»`, `:`, `>`, `|` or two spaces) and takes the rest as the
message, which also works for players on other servers of a network. If that doesn't fit, the first online player's
name in the line is the sender (`chatBot customFormat on/off`, on by default). That also means a server message like `Steve hat ChilloutVR getötet`
counts as Steve talking to the bot. If that's a problem, set up Zenith's `chatSchema` for the server and turn
`customFormat` off.

When a message mentions the bot but it doesn't answer, the log says why (ignored, cooldown, no API key, the message came
from the account itself, or Claude chose not to answer).

## Costs
Each answer is one Claude API request with the recent chat in it, billed to the API key's account. With the default
cooldowns that's at most 8 requests a minute. For a cheaper bot, use `chatBot model claude-haiku-5-5` or lower the
`context`.
