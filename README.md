# TeamSplit

A Paper plugin for Minecraft 1.21.11 (it also runs on Purpur). It splits online players into balanced teams and lets you run any command on a whole team at once. It was built for recording Minecraft movies: split the extras into sides for a war scene, keep the director and cast out of it, send each side where it needs to be, then clear everything when the take is done.

Teams are ordinary vanilla scoreboard teams. That means `@a[team=red]` works in any command, names show in the team color, and vanilla `/team modify` still works on them. TeamSplit only touches the teams it created itself. Scoreboard teams from other plugins or made by hand are left alone.

## Install

1. Download `TeamSplit-<version>.jar` from the latest [Build workflow run](../../actions/workflows/build.yml) (under **Artifacts**), or build it yourself (see [Building](#building)).
2. Put it in your server's `plugins/` folder and restart.
3. Optionally edit `plugins/TeamSplit/config.yml`, then run `/teams reload`.

## Quick start

```
/teams exclude Director Cast1 Cast2           keep yourself and the cast out of splits
/teams split 2                                everyone else becomes red vs blue
/teamrun red tp {player} -120 64 30           send red to their side
/teamrun blue tp {player} 120 64 30           and blue to theirs
/teamrun all give {player} iron_sword         arm everyone
/teams glow all on                            check the sides at a glance...
/teams glow all off                           ...then turn the glow off before filming
/teams clear                                  done recording: delete the teams
```

## Commands

| Command | What it does |
|---|---|
| `/teams split <count> [names...]` | Deletes the previous TeamSplit teams, shuffles every eligible online player, and deals them into `count` teams. Team sizes differ by at most one. |
| `/teams create <name> [color]` | Makes an empty team for a hand-picked scene. |
| `/teams add <team> <players>` | Puts players on a team, moving them off any other team. List as many names and selectors as you like, e.g. `/teams add red Steve Alex @a[distance=..10]`. |
| `/teams remove <players>` | Takes players off their team. |
| `/teams exclude <players>` | Keeps players out of `/teams split` (saved across restarts) and takes them off their current team. `/teams add` still works for them. |
| `/teams include <players>` | Lets excluded players be split again. |
| `/teams color <team> <color>` | Changes a team's color. |
| `/teams glow <team\|all> on\|off` | Makes members glow in their team color. |
| `/teams colors on\|off` | Shows or hides team colors on names, nametags and glow, for every team (including ones made later). Each team's color is remembered, so `on` brings it back. |
| `/teams nametags show\|hide` | Shows or hides nametags for every team (including ones made later). |
| `/teams list` | Lists each team and its members (offline members are grayed out), then the excluded players and any online players who aren't on a team. |
| `/teams disband <team>` | Deletes one team. |
| `/teams clear` | Deletes every TeamSplit team and removes the glow TeamSplit added. |
| `/teams reload` | Reloads `config.yml`. |
| `/teamrun <team\|all> [--as\|--sudo] [--delay <ticks>] <command...>` | Runs a command once for each online member of a team, or of every team with `all`. |

Every argument tab-completes: team names, players, colors, flags, and even the command you pass to `/teamrun`.

### Splitting

- `/teams split 3` makes teams named after colors: red, blue, green, yellow, aqua, pink, gold, purple, white, gray, dark_red, dark_blue, dark_green, dark_aqua, dark_gray, black. Past 16 teams it continues with team17, team18, and so on.
- `/teams split 3 knights mages rogues` uses your own names. If you give fewer names than teams, color names fill the rest.
- The count must be at least 2. If you ask for more teams than there are players to split, you get one team per player instead, so no team is empty.
- Each split deletes the previous TeamSplit teams first, so nothing is left over from the last take.
- These players are never split:
  - anyone on the exclude list (`/teams exclude`)
  - anyone with the `teamsplit.exempt` permission
  - spectators (configurable)
  - vanished players (configurable)
- A player who is on a scoreboard team from another plugin is moved to their new TeamSplit team, because vanilla allows only one team per player.

### Running commands on a team

`/teamrun` runs the command once per online member. It runs the commands as **you**, with your permissions. That means `~ ~ ~` is your own position and the command output shows up in your chat.

Placeholders:

- `{player}` is replaced with each member's name.
- `{team}` is replaced with that member's team name.

| Example | Result |
|---|---|
| `/teamrun red give {player} diamond 5` | Gives each red member 5 diamonds. |
| `/teamrun red tp {player} ~ ~ ~` | Brings the whole red team to you. |
| `/teamrun blue --as tp @s ~ ~10 ~` | `--as` runs `execute as <member> at @s run ...`, so `@s` and `~ ~ ~` mean the member. This launches each blue member 10 blocks up from where they stand. |
| `/teamrun red gamemode adventure` | A command without `{player}` automatically runs as each member (like `--as`), so this works as you'd expect. |
| `/teamrun all --delay 20 title {player} title "Action!"` | `--delay <ticks>` runs it for one member at a time, 20 ticks (1 second) apart. |
| `/teamrun red --sudo warp arena` | `--sudo` makes each member run the command themselves, with their own permissions. Use it for plugin commands that only affect whoever runs them. Needs `teamsplit.run.sudo`. |
| `/teamrun all tellraw {player} "You are on team {team}"` | Tells everyone which team they're on. |

For plugin commands, prefer `{player}` (for example `/teamrun red kit knight {player}`) or `--sudo`. `--as` relies on vanilla `/execute`, which some plugin commands don't understand.

Vanilla selectors keep working without `/teamrun`, too: `/effect give @a[team=blue] glowing 10`.

### Glow

`/teams glow red on` makes red members glow red, which is handy for checking who's on which side during setup. Remember to turn it off (or run `/teams clear`) before you film. TeamSplit remembers whose glow it turned on. Players who were offline at the time get their glow fixed when they rejoin. Glow from other sources, such as the Glowing effect or spectral arrows, is never touched.

### Getting teams out of the shot

Team colors and nametags help while you set up a scene but get in the way on camera. Before you roll:

```
/teams glow all off          stop the glow
/teams colors off            names and nametags go back to normal (white)
/teams nametags hide         or hide nametags completely
```

Turn them back on with `/teams colors on` and `/teams nametags show`. Both settings are saved, so they stay the way you left them for new splits and after a restart. The teams themselves keep working the whole time: `/teamrun` and `@a[team=red]` don't care about colors.

### Nicknames

TeamSplit works with nickname plugins that rename players, such as [Rename](https://github.com/exiledddev/rename):
- Team members are matched by their current name, so a renamed player stays on their team.
- In `/teamrun`, `{player}` becomes the player's UUID whenever their name can't be looked up, so commands still reach them. Vanilla commands and `execute as` accept UUIDs.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `teamsplit.manage` | op | All `/teams` commands |
| `teamsplit.run` | op | `/teamrun` |
| `teamsplit.run.sudo` | op | `/teamrun --sudo` |
| `teamsplit.exempt` | nobody | Never being put on a team by `/teams split`. Give it to your director and cast with a permissions plugin as another way to exclude them. |

## Configuration

`plugins/TeamSplit/config.yml`:

```yaml
split:
  exclude-spectators: true      # leave spectators out of /teams split
  exclude-vanished: true        # leave vanished players out of /teams split

team-defaults:                  # applied to teams created from now on
  friendly-fire: true
  see-friendly-invisibles: true
  color: true                   # show team colors (/teams colors on|off overrides this)
  nametag-visibility: always    # always, never, hide-for-other-teams, hide-for-own-team (/teams nametags overrides this)
  collision: always             # always, never, push-other-teams, push-own-team
  prefix: ""                    # e.g. "[<team>] " to show [red] before names; empty = color only
```

To change a team that already exists, use vanilla commands, for example `/team modify red friendlyFire false`.

TeamSplit stores the names of the teams it made, the exclude list, and the list of players it made glow in `plugins/TeamSplit/data.yml`, so everything survives a restart.

## Building

You need JDK 21.

```
./gradlew build          # jar ends up in build/libs/
./gradlew runServer      # starts a local Paper 1.21.11 test server with the plugin installed
```
