# ScriptForge Suite
Made With Claude.
Record a macro once inside Minecraft, then replay it on as many accounts as you like, each one optionally behind its own proxy.

| Module | What it is |
|---|---|
| `mod/` | **Fabric mod, Minecraft 1.21.11.** A JavaScript scripting engine (chat, movement, mouse, GUI, HUD, inventory) plus the **macro recorder** that writes `.sfmacro` files. |
| `headless/` | **Standalone Java client, no Minecraft install needed.** Logs several accounts into a server and replays a `.sfmacro` on each, with per-account proxies. |
| `common/` | The `.sfmacro` file format, shared by both (compiled into the mod, used as a library by the headless client). |

> **Read this first**
> - Many servers ban automation and multi-accounting. Use this on servers you run or where it is explicitly allowed. Proxies do not make it allowed, and using them to dodge a ban or a per-IP limit breaks most server rules and Mojang's EULA.
> - Scripts are trusted code, like mods. The sandbox keeps them to the ScriptForge API, but only run scripts you trust.
> - **Nothing here has been run against a real Minecraft client or server.** See [What was and was not verified](#what-was-and-was-not-verified). Expect to fix a few things on the first build.

---

## Build

You need JDK 21 and Gradle 9.2 or newer (or open the folder in IntelliJ IDEA, which sets this up).

```bash
gradle wrapper --gradle-version 9.2.1   # once; any newer 9.x works too
./gradlew :mod:build                    # -> mod/build/libs/scriptforge-2.0.0.jar
./gradlew :headless:fatJar              # -> headless/build/libs/scriptforge-headless-all.jar
./gradlew -PheadlessOnly :headless:fatJar   # headless client only: skips the Fabric/Minecraft toolchain
```

**Mod** goes into `.minecraft/mods/` with Fabric Loader 0.18.4+ and Fabric API 0.141.3+1.21.11. On first start it creates `.minecraft/scriptforge/` with example scripts.

**Headless client** needs only Java 21: `java -jar scriptforge-headless-all.jar ...`

### MCProtocolLib version

The headless client uses [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib). It never published a release for 1.21.11 (the last release is `1.21.7-1`); only `1.21.11-SNAPSHOT` exists, built from commit `074f0b35d8763ba9b6e4e7fa9f5a45c30ff5543e`. `gradle.properties` points at that snapshot. If it has been removed from `repo.opencollab.dev`:

```bash
git clone https://github.com/GeyserMC/MCProtocolLib && cd MCProtocolLib
git checkout 074f0b35d8763ba9b6e4e7fa9f5a45c30ff5543e
./gradlew publishToMavenLocal          # the headless build reads mavenLocal first
```

---

## Workflow

### 1. Record (mod)

1. Stand exactly where every bot will start, **on the same spot of the block grid** (see [How replay works](#how-replay-works)).
2. `/script record start`, or run the `macro_recorder.js` example and press **F8** to stop.
3. Do the task: walk, mine, place, chat.
4. `/script record stop farm` writes `.minecraft/scriptforge/macros/farm.sfmacro`.
5. Test it with `/script play farm` (add a loop count: `/script play farm 3`).

Recorded every tick: position, rotation, on-ground, horizontal collision, held movement keys, hotbar slot. Recorded as events: starting and finishing a block break, using a block (placing, opening), using an item, arm swings, chat messages and commands. Commands that carry credentials (`/login`, `/register`, `/password`, ...) are never recorded.

**Not recorded:** inventory and container clicks, attacking or using entities, riding, elytra. A macro that needs those has to be scripted in the mod instead.

### 2. Configure (headless)

Copy `headless/headless.example.json` to `headless.json`, put the `.sfmacro` next to it, edit. Fields (all optional except `macro` and `accounts`):

| Field | Default | Meaning |
|---|---|---|
| `server` | `localhost:25565` | `host` or `host:port` |
| `macro` | | `.sfmacro` path; relative paths resolve against the config file |
| `mode` | `relative` | `relative`: shift the macro to wherever each bot stands. `absolute`: use recorded coordinates as they are (the bot must already be at the recorded start) |
| `loops` | `1` | repeats; `0` = forever |
| `loopDelayTicks` | `40` | pause between loops (20 ticks = 1 s) |
| `startDelayTicks` | `100` | wait after joining before doing anything |
| `settleTicks` | `40` | wait after the last join command before the macro starts |
| `joinDelayMs` | `5000` | gap between two accounts connecting |
| `maxSetbacks` | `3` | stop an account when the server has corrected its position more often than this |
| `disconnectWhenDone` | `true` | leave after the last loop; if `false` the bot stays online (Ctrl+C to quit) |
| `reconnects` / `reconnectDelaySeconds` | `0` / `30` | retries after an unexpected disconnect; the macro then restarts from the beginning |
| `replayChat` / `replayCommands` | `true` | send the chat messages / commands that were recorded |
| `joinCommands` | `[]` | `{ "text": "/home farm", "delayTicks": 40 }`, sent by every account after its own list |
| `proxies` | `[]` | proxy pool, handed out round-robin to accounts without their own |
| `authProxy` | game proxy | proxy for Mojang's session server; `"none"` for direct |
| `accounts[]` | | `name`, `mode`, `proxy`, `authProxy`, `joinCommands` (sent first: put `/login` here), plus `uuid` and `accessToken` for `token` accounts |

### 3. Check and run

```bash
java -jar scriptforge-headless-all.jar info  macros/farm.sfmacro   # what is in the recording
java -jar scriptforge-headless-all.jar check headless.json         # pings the server through every proxy
java -jar scriptforge-headless-all.jar run   headless.json
```

`check` reports latency, version and player count per proxy, tests the route to Mojang for online-mode accounts, and warns if the server enforces secure chat. `run` prints a status table every 30 seconds. Ctrl+C disconnects every bot.

---

## Accounts

- **`offline`**: for offline-mode servers (`online-mode=false`, or a proxy network with auth plugins). Put the plugin's login in the account's `joinCommands`; those commands are never written to the log.
- **`token`**: for online-mode servers. You supply the account's `uuid` and a valid Minecraft **access token**. Prefer `"accessToken": "env:MY_VAR"` over putting it in the file. This project does not do the Microsoft login itself, and access tokens expire (about a day), so long unattended runs on real accounts need a fresh token each time. Only use accounts you own.

An offline account connecting to an online-mode server is rejected with a message saying so.

## Proxies

Supported: `socks5://[user:pass@]host:port`, `socks4://host:port`, `http://[user:pass@]host:port` (the HTTP proxy must allow `CONNECT` to the Minecraft port), and `none` for a direct connection. Passwords may be URL-encoded (`%40` for `@`) and are masked in all output.

- Accounts without their own `proxy` take proxies from `proxies` in order, wrapping around. More accounts than proxies means shared IPs, and the client warns about it.
- An account with no proxy at all connects from this machine's IP, and the client warns about that too.
- Online-mode login talks to Mojang's session server over HTTPS. By default that goes through the same proxy as the game connection, so one account shows one IP. Override with `authProxy`, per account or globally.
- MCProtocolLib resolves the server's hostname (and its SRV record) **locally** before it tunnels through the proxy. The game server only ever sees the proxy's IP, but your own DNS resolver sees the lookup. Put the server's IP in `server` if that matters.

---

## How replay works

The headless client has **no physics and no world**. For every tick of the recording it sends the packets the real client sent: the same positions, the same movement-packet type the vanilla client would pick, held keys, hotbar changes, block digging, block use, chat. The server does all the simulation.

- **Relative mode** shifts every position and every block target by a whole number of blocks, so each bot walks the same path *relative to the block grid*. It therefore snaps by at most half a block on its first move; start bots on the same spot within the block that you recorded from.
- The world around each bot has to match the world around the recording, shifted by that offset (the same farm layout, the same tunnel). The client cannot see blocks, so it cannot notice a mismatch other than through the server's reaction.
- If the server moves a bot (a setback or teleport), the client confirms it, re-bases the macro from there and counts it. After `maxSetbacks` it stops that account instead of replaying a desynced path.
- If a bot dies it respawns and its macro stops.
- With several loops, each loop begins where the previous one ended. A macro that finishes where it began repeats in place; one that walks away (a tunnel) keeps going.
- Anything that reacts to latency, tool durability, mob interference, or randomness will drift from the recording; the replay is open-loop.

**Chat is unsigned.** Servers with `enforce-secure-profile=true` reject it, and vanilla servers refuse unsigned versions of commands with signed arguments (`/msg`, `/say`, `/me`). `check` tells you whether the server enforces secure chat.

---

## Scripting API (mod)

Scripts are JavaScript (Rhino 1.7.15, ES6 syntax: `let`/`const`, arrow functions; no `class`, `async` or Promises) in `.minecraft/scriptforge/scripts/`. `sleep(500)` and friends block, so a macro reads top to bottom.

| What | How |
|---|---|
| Script manager GUI | `Home` key or `/script gui` |
| Panic button | `End` stops every script (rebindable in Controls) |
| Commands | `/script list`, `/script run <name>`, `/script stop <name>`, `/script stopall` |
| Macros | `/script record start`, `/script record stop <name>`, `/script record status`, `/script play <name> [loops]` |
| Hotkey a script | `// @hotkey F7` at the top (press to start, again to stop) |
| Auto-start on world join | `// @autorun` |
| Description in lists | `// @description What it does` |

Folders under `.minecraft/scriptforge/`: `scripts/`, `data/` (for `fs.*`), `macros/` (`.sfmacro`), `recordings/` (chat logs). Leaving a world stops all scripts.

Each script gets its own thread. `sleep()`, `waitTicks()`, `waitUntil()` and `chat.waitFor()` block that script only; while it is blocked (or after the body ends) event handlers, timers and GUI callbacks run on the same thread, so there are no race conditions. A script ends when its body finishes and it has no handlers or timers left.

**Globals**: `print(...)`, `sleep(ms)`, `waitTicks(n)`, `waitUntil(fn, timeoutMs)` -> bool, `setTimeout(fn, ms)`, `setInterval(fn, ms)`, `clearTimeout(id)`, `clearInterval(id)`, `exit()`.

**chat**: `send(text)` (a leading `/` sends a command), `command(cmd)`, `print(text)` (local, `&a` colors), `history(n)`, `search(regex)`, `waitFor(regex, timeoutMs)` -> text|null, `record(file, {outgoing, filter})`, `stopRecording()`, `isRecording()`.

**player**: `name() x() y() z() pos() yaw() pitch() health() maxHealth() food() onGround() sprinting() sneaking() speed() dimension() gamemode() inWorld()`, `slot()`, `selectSlot(0-8)`, `heldItem()`, `inventory()`, `count(id)`, `findItem(id)`, `setRotation(yaw, pitch)`, `lookTo(yaw, pitch, ticks)`, `lookAt(x,y,z[,ticks])`.

**world**: `blockAt(x,y,z)`, `target()` (crosshair), `time()`, `dayTime()`, `raining()`, `server()`.

**input** (keys: forward back left right jump sneak sprint attack use pick drop swap inventory hotbar1-9): `hold(key)`, `release(key)`, `tap(key[,ticks])`, `releaseAll()`, `isDown(key)`, `isKeyDown("r")`, `keys()`.

**move**: `walk(dir, ticks)`, `forward/back/left/right(ticks)`, `jump()`, `sprint(bool)`, `sneak(bool)`, `stop()`, `walkTo(x, z, {sprint, radius, timeout, jump, turnSpeed})` -> reached (straight line, no pathfinding).

**mouse**: `x() y() pos()`, `isDown(btn)`, `look(dx, dy)`, `moveTo(x, y)`, `click([btn])`, `clickAt(x, y, btn)`, `scroll(n)`, `hold(btn)`, `release(btn)`. In the world, clicks map to attack/use/pick; with a screen open they go to the screen.

**gui**: `current()`, `isOpen()`, `close()`, `openInventory()`, `slots()`, `clickSlot(slot[,button,mode])`, `shiftClick(slot)`, `toast(title, msg)`, `screen("Title")` -> builder with `.label([id,]x,y,text)`, `.button(x,y,w,h,text,fn)`, `.textField(id,x,y,w,h,placeholder)`, `.setText(id,text)`, `.getText(id)`, `.onClose(fn)`, `.open()`, `.close()`, `.isOpen()` (positions are offsets from the screen center).

**hud**: `text(id,x,y,text[,color])`, `rect(id,x,y,w,h,color)`, `remove(id)`, `clear()` (negative x/y anchor to right/bottom).

**events**: `on("chat"|"send"|"tick", fn)`, `off(name)`, `onKey("r", fn)`, `offKey("r")`, `clear()`. `chat` -> `{type, sender, text, content, time}`; `send` -> `{text, command}`.

**fs** (sandboxed to `scriptforge/data`): `read`, `write`, `append`, `appendLine`, `exists`, `delete`, `list`.

**macro**: `record()`, `stop(name)` -> ticks, `play(name[,loops])`, `list()`, `exists(name)`, `info(name)`, `isRecording()`.

```js
// @hotkey G
chat.send("/home farm");
chat.waitFor("teleported", 5000);
for (let i = 0; i < 20; i++) {
    input.tap("use");
    sleep(250);
}
move.walkTo(player.x() + 10, player.z(), { sprint: true });
```

---

## What was and was not verified

Verified here:
- **Headless client:** every call into MCProtocolLib was checked against the library's own source at commit `074f0b35` (the 1.21.11 state): packet constructors, field order, enums, session, listener and proxy APIs. The code compiles against signature stubs generated from that source, and the replay engine and the bot's join/command/macro/disconnect sequence were run in simulation, including offsets, setbacks, loop timing and the packet order.
- **Mod:** every source file parses without syntax errors, and the Gradle scripts evaluate.

Not verified:
- The headless client has **never connected to a real server** and was never compiled against the real MCProtocolLib jar (the sandbox could not download it).
- The mod has **never been compiled against Minecraft 1.21.11** or run in game, so Yarn names or Fabric API signatures may be off by a rename.
- Whether a given server's anti-cheat accepts the replayed movement.

## If the build fails

- **Mod, "cannot find symbol" on a Minecraft class or method.** Those names are Yarn 1.21.11+build.4. The places most likely to need a touch: key binding categories and HUD registration in `ScriptForge.java`; screen click handling in `gui/*` and `api/MouseApi.java`; inventory and slot access in `api/GuiApi.java` and `core/ItemInfo.java`; the recorder callbacks in `core/RecorderHooks.java`; chat events in `core/ChatHooks.java`. If `yarn_mappings` is not found, take the current value for 1.21.11 from <https://fabricmc.net/develop>.
- **Loom not found.** Use the Loom version that page lists and set `loom_version` in `gradle.properties`. Yarn stops at 1.21.11; the 26.x versions need Mojang mappings and a newer Loom plugin id.
- **Headless, library errors.** Only two files touch MCProtocolLib's API: `PacketFactory.java` (everything the client sends) and `Bot.java` / `ProxyCheck.java` (sessions and incoming packets). A different library version means changing those.
- **Headless connects but gets kicked.** Start it with `java -Dsf.debug=1 -Dorg.slf4j.simpleLogger.log.dev.scriptforge=debug -jar ...` for stack traces and the server's chat messages. Typical causes: secure chat enforced, online-mode server with an `offline` account, or the world not matching the recording (watch for setback messages).

## Porting to other Minecraft versions

Game access in the mod is isolated in `core/Mc`, `core/Keys`, `core/Snapshot` and the `api/` and `gui/` classes. For another 1.21.x, change the versions in `gradle.properties` and fix renamed Yarn methods. For the headless client, use the MCProtocolLib build for that version. The `.sfmacro` format does not depend on the Minecraft version.
