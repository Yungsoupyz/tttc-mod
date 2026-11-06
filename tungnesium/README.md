# Tungnesium

Event client mod (Fabric, Minecraft 1.21.8) + Paper plugin.

```
mod/      the client mod   -> mod/build/libs/tungnesium-1.0.0.jar      (players put in .minecraft/mods)
plugin/   the Paper plugin -> plugin/build/libs/tungnesium-plugin-1.0.0.jar (server plugins folder)
```

## Build (pick one)

**A. GitHub (no setup, works from school)**
1. Make a free GitHub repo, upload this whole folder (keep `.github/workflows/build.yml`).
2. Repo > Actions > "Build Tungnesium" > latest run > Artifacts > `tungnesium-jars`.

**B. Your PC** (needs Java 21): run `./gradlew build` inside `mod/` and again inside `plugin/` (`gradlew.bat build` on Windows).

If the build prints compile errors, send them to Claude; the source has not been compiled yet.

## Players need
Fabric Loader + Fabric API for 1.21.8 + `tungnesium-1.0.0.jar`.

## Server setup
1. Put the plugin jar in `plugins/`, start once, edit `plugins/Tungnesium/config.yml`, run `/tungnesium reload`.
2. Players without the mod are kicked after 20 s with your Discord link.
3. Staff who test with other mods: give permission `tungnesium.bypass`.

## What it does
| Feature | How |
|---|---|
| Loading screens | `/tungnesium loading ace_race @a 8` (game id, who, seconds, optional subtitle). `/tungnesium clearloading @a` closes it. Art: put `assets/tungnesium/textures/loading/<game_id>.png` in the mod (or a resource pack) |
| Tab list | Cards per team with heads, team points. Automatic |
| Scoreboard | Warm themed sidebar, hides numbers if your objective uses a blank number format |
| Standings screen | Key **J**, `/events`, or the Esc menu button. Tabs: Teams, Players, Games |
| Mod check | On join the mod sends its mod list + enabled resource packs. Deny-list by default (config `modcheck`). `/tungnesium mods <player>` shows what they run |
| Auto update | Set `update.latest-version`, `update.url`, `update.sha256` in config. Old clients see an "Update now" screen |
| Rename teams | `teams:` in config.yml (name, color, icon). Reload. No mod update needed |

Standings come from the **main scoreboard**. Fill in `games:` objective names and (optionally) the total objectives in config.yml.
From a datapack/Skript you can call the commands, e.g. Skript: `execute console command "tungnesium loading pitch_perfect @a 6"`.

## Honest limits
- The mod check runs on the player's computer. A determined cheater can fake it. Treat it as a filter, not proof.
- Esc-menu and Tab/scoreboard replacements hook into Minecraft internals; if a future version changes them the vanilla UI simply shows instead.
- Publishing an update: upload the new jar to GitHub Releases (or Discord), put its link and SHA-256 in config, reload.
