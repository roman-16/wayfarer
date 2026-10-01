---
name: exaroton
description: Record everything the exaroton API exposes about the Wayfarer server - console, status and players, memory, tick time, the server log, region files and the Distant Horizons database - into exaroton/ until the user presses Escape. Use when the user asks to record, monitor, watch or log the exaroton server, or wants its metrics kept while they play.
---

# Exaroton

`just exaroton` records the account's server into `exaroton/<YYYY-MM-DD_HH-MM>/`, in the repository root and ignored by git. It needs `EXAROTON_API_TOKEN`, which devbox loads from `.env`; never print the token or write it anywhere else. With more than one server on the account, pass its name: `just exaroton roman168`.

## Record

Run `just exaroton` in the foreground with no timeout. It never returns on its own: it records until the user presses Escape, which kills it and ends the turn. Never start it in the background. Its output is one line per status change, stream start and reconnect. A server that is offline is recorded too: the recorder waits and picks up the boot once the server starts.

The recorder only reads. Never send console commands, and never call `start`, `stop`, `restart`, `command`, `extend-time`, `logs/share` or any `POST`, `PUT` or `DELETE` endpoint, nor any websocket message other than a stream `start`; exaroton makes `start`, `stop` and `restart` plain `GET`s, so reading is not safe by method alone.

## What a session holds

| File | Holds |
|---|---|
| `session.json` | At the start: the server object, its RAM, every `server.properties` value and the mods folder with sizes |
| `console.log` | Every console line as it arrives, without ANSI codes; lines carry only `[HH:MM:SS]` |
| `streams.jsonl` | One timestamped JSON line per websocket message: `status` (with players), `heap` (`usage` in bytes), `stats` (`memory.usage`, `memory.percent`), `tick` (`averageTickTime` in ms), `connected`, `disconnected` |
| `polls.jsonl` | Every minute: status, players, region file count, empty region files, region bytes and the Distant Horizons database size, or the error that stopped the poll |
| `latest.log` | The full server log with millisecond timestamps, refreshed every five minutes while the server runs and once when it stops or crashes; while it runs it lags about ten minutes behind |

Every line is written the moment it arrives, so a session is complete up to the instant it was stopped. The tick stream has stayed silent on this server, and exaroton reports no CPU usage; "Can't keep up!" lines in `console.log` and `latest.log` are the measure of server lag.
