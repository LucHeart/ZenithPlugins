# ZenithChat (ZenithProxy plugin)

Sends chat messages and DMs as the proxy account, from a command or from other programs over a REST endpoint.
Messages go out one at a time, `messageDelayMs` (1500) apart, so a burst doesn't get the account kicked for spam.

Messages are only accepted while the account is in game (connected and out of queue). Queued messages are dropped
when it disconnects.

## Install
See the [repository README](../README.md#install).

## Commands
- `zenithChat on/off` (alias `zc`)
- `zenithChat say <message>`: public chat. A leading `/` runs it as a server command
- `zenithChat dm <player> <message>`: sent with ZenithProxy's whisper command (`/msg` by default,
  `extraChat whisperCommand <command>` changes it)
- `zenithChat delay <ms>`: time between two messages
- `zenithChat api on/off`: the REST endpoint, off by default. The first time it's turned on a token is generated
- `zenithChat api port <port>` (default 9431)
- `zenithChat api bind <address>` (default `127.0.0.1`, only the same machine can reach it). Use `0.0.0.0` if the
  caller is on another host or ZenithProxy runs in Docker (then also publish the port, e.g. `-p 9431:9431`)
- `zenithChat api token`: shows the token, `api token new` replaces it
- `zenithChat api commands on/off` (default off): let messages from the endpoint start with `/`, i.e. run any server
  command as the account

Messages longer than 256 characters (for DMs including `/msg <player> `) are turned away instead of being cut off.

## REST endpoint
Every request needs `Authorization: Bearer <token>`. Bodies are JSON.

| Request | Body | |
|---|---|---|
| `POST /chat` | `{"message": "hello"}` | Public chat |
| `POST /dm` | `{"player": "Steve", "message": "hello"}` | DM a player |
| `POST /dm` | `{"players": ["Steve", "Alex"], "message": "hello"}` | DM those of them who are online (up to 100 names) |
| `GET /status` | | `{"online": true, "queued": 0}` |

Responses are JSON. `202 {"queued": true}` means the message was accepted and will be sent shortly, everything else
has `{"error": "..."}`.

A DM to a list only goes to players in the tab list, and never to the account itself. It answers
`202 {"queued": ["Steve"], "offline": ["Alex"]}`, with the names as they appear in game. It's all or nothing: if the
queue can't take all of them, none are sent and it answers 429.

| Status | Meaning |
|---|---|
| 400 | Bad JSON, empty message, invalid player name, or too long |
| 401 | Missing or wrong token |
| 403 | Message starts with `/` and `api commands` is off |
| 429 | Too many messages waiting (`maxQueuedMessages`, 20) |
| 503 | Account not in game |

```sh
curl -X POST http://127.0.0.1:9431/dm \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"player": "Steve", "message": "Hi! This area is claimed."}'
```
