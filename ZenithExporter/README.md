# ZenithExporter (ZenithProxy plugin)

Prometheus metrics for ZenithProxy: players online on the server, the queue, the proxy's connection, the account,
the world around it, who's connected to the proxy, and the JVM.

## Install
Download the jar matching your ZenithProxy MC version (check with the `status` command) from the releases,
e.g. `ZenithExporter-1.0.0+26.3.0.jar`. Put it in the `plugins/` folder next to the Zenith launcher and restart.
Requires the `java` release channel. 1.21.4 builds run on Java 21, 26.x builds need Java 25.

It's off until you run `zenithExporter on`, and remembers that across restarts.

The endpoint is then on `http://127.0.0.1:9430/metrics`, so only the same machine can reach it. Change the port with
`zenithExporter port <port>`. If Prometheus is on another host or Zenith runs in Docker, run
`zenithExporter bind 0.0.0.0` (and in Docker publish the port, e.g. `-p 9430:9430`), and consider
`zenithExporter auth <username> <password>`.

```yaml
scrape_configs:
  - job_name: zenith
    scrape_interval: 15s
    static_configs:
      - targets: ["zenith-host:9430"]
    # with auth on
    # basic_auth: { username: scraper, password: secret }
```

For more accounts, scrape each proxy on its own port. `zenith_client_info` has the username and server of each one.

`grafana/zenith-dashboard.json` is a dashboard to import (Dashboards → New → Import, made on Grafana 13, needs a
Prometheus data source), `prometheus/zenith-alerts.yml` has alert rules.

## Players online

`zenith_server_players_online` has one series per way of knowing the number, because they can't always all be known
and don't always agree:

| `source`  | Where it comes from                                        | Available           |
|-----------|------------------------------------------------------------|---------------------|
| `footer`  | What the server prints in its tab list footer (2b2t does)  | In game             |
| `tablist` | Listed entries on the tab list                             | In game             |
| `status`  | The server list ping                                       | Always, also offline |

Don't `sum()` across sources. For one number use `max without (source) (zenith_server_players_online)`.

2b2t's list ping counts the queue as online, so for it `status` uses the in-game number from the ping's player
sample instead, and the raw count is in `zenith_server_status_players_reported`. That also means 2b2t's in-game count
and queue lengths are known without the proxy being connected, or logged in at all.

### Every player

`zenith_server_player_online{player,uuid,gamemode}` has a series for each player on the tab list, and
`zenith_server_player_latency_seconds{player,uuid}` their latency. So Prometheus keeps a history of who was online when,
for as long as its retention (15 days by default):

```promql
# everyone online right now
zenith_server_player_online
# when was this player on
max by (player) (zenith_server_player_online{player="Steve"})
# everyone seen in the last day, with seconds online
sum by (player) (count_over_time((max by (player) (zenith_server_player_online))[1d:1m])) * 60
```

A missing series means offline, or the proxy not being in game to see, so check `zenith_client_state` next to it. A player
who joins and leaves between two scrapes isn't seen. It only works while connected, the tab list isn't known otherwise.

Every player that has ever been seen adds series to Prometheus: a 600 player tab list adds about 1200 series and 160 KB per
scrape, and on a server with many different players those add up over the retention. `zenithExporter players off` turns
them off if that's too much.

`zenith_server_watched_player_online{player}` is for specific players, see `watch add`. It reports an explicit 0 while
they're offline, which makes it the one to alert on. It keeps working with `players off`.

## Metrics

Values only known in game (health, tab list, chunks...) leave the series out while not in game, instead of
reporting a 0 or a stale value. Graphs show a gap and alerts on low values don't go off during a disconnect.
Counters keep their values until the proxy restarts.

**Client**: `zenith_client_state{state}`, `zenith_client_connected`, `zenith_client_ping_seconds`,
`zenith_client_online_duration_seconds`, `zenith_client_connect_timestamp_seconds`,
`zenith_client_disconnect_timestamp_seconds`, `zenith_client_protocol_version`, `zenith_client_info{username,server}`,
`zenith_build_info{zenith_version,mc_version,exporter_version}`, `zenith_client_connects_total`,
`zenith_client_disconnects_total{reason}`, `zenith_client_login_failures_total`, `zenith_client_auto_reconnects_total`,
`zenith_client_received_bytes_total`, `zenith_client_sent_bytes_total`, `zenith_client_session_duration_seconds` (histogram)

**Queue**: `zenith_queue_position`, `zenith_queue_eta_seconds`, `zenith_queue_priority`,
`zenith_2b2t_queue_length{queue}`, `zenith_queue_starts_total`, `zenith_queue_skips_total`,
`zenith_queue_wait_seconds` (histogram)

**Server**: `zenith_server_players_online{source}`, `zenith_server_players_max`, `zenith_server_tablist_entries`,
`zenith_server_players_latency_avg_seconds`, `zenith_server_player_joins_total`, `zenith_server_player_leaves_total`,
`zenith_server_player_online{player,uuid,gamemode}`, `zenith_server_player_latency_seconds{player,uuid}`,
`zenith_server_watched_player_online{player}`, `zenith_server_watched_player_latency_seconds{player}`,
`zenith_server_tps`, `zenith_server_reported_tps`, `zenith_server_reported_ping_seconds`, `zenith_server_status_up`,
`zenith_server_status_players_reported`, `zenith_server_status_ping_duration_seconds`, `zenith_server_status_protocol_version`, `zenith_server_restart_notices_total`

**Account**: `zenith_player_health`, `zenith_player_food`, `zenith_player_saturation`, `zenith_player_alive`,
`zenith_player_experience_level`, `zenith_player_experience_progress_ratio`, `zenith_player_experience_points`,
`zenith_player_being_controlled`, `zenith_player_pathing`, `zenith_player_position_blocks{axis}`,
`zenith_player_gamemode{gamemode}`, `zenith_player_effect_amplifier{effect}`, `zenith_player_inventory_items{item}`,
`zenith_player_equipment_durability_ratio{slot,item}`, `zenith_player_moved_blocks_total`, `zenith_player_deaths_total`,
`zenith_player_totem_pops_total`, `zenith_player_damage_taken_total`, `zenith_player_attacked_by_players_total`

**World**: `zenith_world_dimension{dimension}`, `zenith_world_chunks_loaded`, `zenith_world_view_distance_chunks`,
`zenith_world_simulation_distance_chunks`, `zenith_world_border_size_blocks`, `zenith_world_age_ticks`,
`zenith_world_raining`, `zenith_world_rain_strength`, `zenith_world_thunder_strength`, `zenith_world_entities{type}`,
`zenith_world_dropped_items{item}`, `zenith_world_players_nearby`, `zenith_world_latest_entity_id`,
`zenith_world_chunk_loads_total`, `zenith_world_entities_spawned_total{type}`, `zenith_world_items_spawned_total{item}`,
`zenith_visual_range_events_total{event,friend}`

**Proxy**: `zenith_proxy_players_connected{role}`, `zenith_proxy_player_ping_seconds{player,role}`,
`zenith_proxy_start_timestamp_seconds`, `zenith_proxy_module_enabled{module}`, `zenith_proxy_plugins_loaded`,
`zenith_proxy_discord_connected`, `zenith_proxy_update_available`, `zenith_proxy_player_connections_total{role}`,
`zenith_proxy_player_disconnections_total{role}`, `zenith_proxy_rejected_connections_total{reason}`,
`zenith_chat_messages_total{type}`, `zenith_module_events_total{event}`

**Exporter**: `zenith_exporter_collector_errors_total{collector}`, should stay empty. One failing metric never fails
the whole scrape, it shows up here and in the log.

**JVM**: the `jvm_*` and `process_*` metrics of the Prometheus Java client.

Coordinates are private on a server like 2b2t, and anyone who can scrape the endpoint would see them, so
`zenith_player_position_blocks` only has `y` until you run `zenithExporter position on`.

## Build
Built from the root of the [ZenithPlugins](../README.md) repository: `./gradlew :ZenithExporter:build -Pmc=26.3.0` →
`ZenithExporter/build/libs/ZenithExporter-<version>+<mc>.jar`. See the root README for MC versions, CI and releases.

Packet classes that move between MC versions are in `src/mc1` and `src/mc26`.

## Commands
- `zenithExporter` (alias `exporter`): show the current settings
- `zenithExporter on/off` (default off)
- `zenithExporter port <port>` (default 9430): applies right away, and if the port can't be used it keeps the old one
- `zenithExporter bind <address>` (default 127.0.0.1)
- `zenithExporter auth <username> <password>` / `auth off`: basic auth on the endpoint
- `zenithExporter jvm on/off` (default on)
- `zenithExporter position on/off` (default off): export x and z
- `zenithExporter players on/off` (default on): a series per player on the tab list
- `zenithExporter statusPing on/off` (default on): ping the server for its player count. Skipped while a connection
  proxy is set, since the ping would show the server this host's real address
- `zenithExporter statusPing interval <seconds>` (default 60)
- `zenithExporter watch add <player>` / `watch remove <player>` / `watch clear`

## AI Disclosure
Most of this project is made with the help of an LLM.
