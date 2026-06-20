# ExamsReloaded

> Exams in Minecraft! Let players take multiple‑choice exams at in‑world signs and automatically reward them with ranks, items, or any console command when they pass.

ExamsReloaded is a Paper plugin that turns a wall sign into an interactive exam terminal. Players right‑click an **Exam** sign to sign up, answer a randomized set of multiple‑choice questions through Minecraft's native dialog screens, and — if they score high enough — the server runs the commands you configured (give a rank, hand out items, broadcast a message, etc.).

It is a modernized continuation of the original *Exams* plugin, rebuilt on the **Paper Dialog API** (Minecraft **1.21.7+**) and integrated with [Vault](https://www.spigotmc.org/resources/vault.34315/) for economy (exam fees) and permissions/ranks.

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Configuration (`config.yml`)](#configuration-configyml)
- [Defining Exams (`exams.yml`)](#defining-exams-examsyml)
- [Exam Signs](#exam-signs)
- [Commands](#commands)
- [Permissions](#permissions)
- [Student Data (`students.yml`)](#student-data-studentsyml)
- [Building From Source](#building-from-source)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Credits](#credits)

---

## Features

- **Sign‑based exams** — place a wall sign, write the exam name, and players interact with it by right‑clicking.
- **Native dialog questions** — each question opens as a real Minecraft dialog screen (Paper Dialog API) with one clickable button per option (A–D); a random subset of an exam's question pool is drawn per attempt. No chat typing required.
- **Optional answer shuffling** — randomize the order of answer options so players can't memorize "always B".
- **Automatic rewards** — run a single command or a list of console commands when a player passes, with `$PlayerName` substitution. Optionally run a command when a player fails.
- **Prerequisites** — gate exams behind a required rank, a required permission node, and/or having already passed another exam.
- **Economy integration** — charge a fee to sit an exam (requires Vault + an economy plugin).
- **Cooldowns** — stop players from re‑attempting an exam too quickly, with a `exams.nocooldown` bypass permission.
- **Time windows** — restrict exams to a window of the Minecraft day/night cycle.
- **Configurable result messages** — customize the end‑of‑exam summary with color codes and placeholders.
- **Automatic cleanup** — purge stale student records, manually (`/exams clean`) or via prerequisite logic.

---

## Requirements

| | |
|---|---|
| **Server** | **Paper** (or a Paper fork) running Minecraft **1.21.7+** — the native Dialog API is required, so plain Spigot/Bukkit and versions below 1.21.7 are **not** supported |
| **Java** | **21+** (Paper 1.21.x requires Java 21; CI builds with JDK 21) |
| **Required dependency** | [Vault](https://www.spigotmc.org/resources/vault.34315/) |
| **Optional** | An economy plugin (for exam fees) and a permissions provider such as LuckPerms (for ranks/permissions) |

Vault is a hard dependency (`depend: [Vault]` in `plugin.yml`) — the plugin will not load without it. Economy features are enabled only if Vault finds a registered economy provider; rank/permission features fall back to Bukkit's built‑in permission checks if no Vault permission provider is found.

---

## Installation

1. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) into your server's `plugins/` folder.
2. (Optional) Install an economy plugin and/or a permissions plugin (e.g. LuckPerms).
3. Download or [build](#building-from-source) `ExamsReloaded` and drop the `.jar` into `plugins/`.
4. Start the server. On first launch the plugin generates:
   - `plugins/ExamsReloaded/config.yml` — global settings
   - `plugins/ExamsReloaded/exams.yml` — two example exams (`Citizen` and `Wizard`)
   - `plugins/ExamsReloaded/students.yml` — created when the first student record is written
5. Edit `exams.yml` to define your own exams, then run `/exams reload`.

---

## Quick Start

1. Start the server once so `exams.yml` is generated with the example `Citizen` exam.
2. Place a wall sign and write:
   ```
   Exam
   In
   Citizen
   ```
   (Line 1 must be `Exam`; line 3 is the exam name. Line 2 is decorative.)
3. Right‑click the sign to sign up. Right‑click again to start.
4. A dialog opens with the question and one button per answer — click the option you think is correct.
5. Each answer opens the next question's dialog. Finish all questions to see your score. Pass, and the exam's reward command runs automatically.

---

## How It Works

The exam lifecycle is split across a handful of managers:

1. **Sign placement** (`BlockListener#onSignChange`) — when a sign whose first line is `Exam` is placed, the plugin verifies the player has `exams.place` (or is OP) and that line 3 names a real exam. Otherwise the sign is cancelled and dropped as an item.
2. **Sign up** (`BlockListener#onPlayerInteract` → `ExamManager#handleNewExamPrerequisites`) — the first right‑click checks all prerequisites (rank, permission, required exam, cooldown, fee). If everything passes, the player is registered as a student and charged any fee.
3. **Start** — the second right‑click generates the exam: it picks `NumberOfQuestions` *distinct* questions at random from the exam's question pool and shows the first one as a native dialog (`ExamManager#doExamQuestion` → `ExamDialog#show`).
4. **Answering** (`ExamDialog` button callback → `ExamManager#submitAnswer` → `StudentManager#answer`) — each answer is a multi‑action dialog button whose server‑side `customClick` callback submits the chosen option letter; it's checked against the stored correct option and correct answers increment the score. The next question's dialog opens automatically until the pool is exhausted.
5. **Result** (`ExamManager#calculateExamResult`) — the score is computed as `100 * correctAnswers / NumberOfQuestions`. If it meets `RequiredExamScore`, the reward command(s) run (via the console) and the exam is recorded as passed; otherwise the optional fail command runs.

State lives in flat YAML files via Bukkit's `FileConfiguration`. There is no database.

---

## Configuration (`config.yml`)

Generated on first run. Reload changes with `/exams reload`.

```yaml
ServerName: "Your Server"        # Shown in /exams info and broadcasts
MinExamTime: 60                  # Cooldown in MINUTES between exam attempts
RequiredExamScore: 80            # Minimum score (0-100) required to pass any exam
Debug: false                     # Verbose logging to console
ShuffleQuestionOptions: false    # Randomize the A-D order of answer options
EndExamMessages:                 # Lines shown after an exam finishes (color codes + placeholders)
  - ""
  - ""
  - ""
  - "&e------------- Exam done -------------"
  - ""
  - "&b Exam Score: &e<score> &bpoints."
  - "&b Points Needed: &e<requiredScore> &bpoints."
```

| Key | Type | Default | Description |
|---|---|---|---|
| `ServerName` | string | `Your Server` | Displayed in plugin info and broadcast messages. |
| `MinExamTime` | int (minutes) | `60` | Minimum time a player must wait between exam attempts. |
| `RequiredExamScore` | int (0–100) | `80` | Percentage score needed to pass. Applies to all exams. |
| `Debug` | boolean | `false` | Enables `logDebug` output for troubleshooting. |
| `ShuffleQuestionOptions` | boolean | `false` | Shuffles answer options each time a question is presented. |
| `EndExamMessages` | list of strings | (see above) | Result summary. Supports `&` color codes and the `<score>` and `<requiredScore>` placeholders. |

> **Note:** The automatic stale‑student cleanup threshold (`autoCleanTime`, ~8 hours) is an internal default and is not currently exposed in `config.yml`.

---

## Defining Exams (`exams.yml`)

Each top‑level key is an exam name. Below is a fully annotated example combining every supported option:

```yaml
Citizen:
  # --- Rewards ---
  Command: "/lp user $PlayerName group add Citizen"   # Single command run on PASS
  # OR use a list of commands instead of a single Command:
  # Commands:
  #   - "/give $PlayerName minecraft:poppy 1"
  #   - "/lp user $PlayerName group add Citizen"
  CommandOnFail: "/say $PlayerName failed the exam"    # Optional command run on FAIL

  # --- Question pool ---
  NumberOfQuestions: 3            # How many questions to draw (must be <= number of questions defined)

  # --- Time window (Minecraft ticks, 0-24000) ---
  StartTime: 600                  # Exam opens at this world time
  EndTime: 13000                  # Exam closes at this world time
                                  # (If StartTime == EndTime, the exam is always open)

  # --- Optional cost ---
  Price: 100                      # Fee charged on signup (requires Vault economy)

  # --- Optional prerequisites ---
  RequiredRank: "Citizen"         # Player must be in this permission group
  RequiredPermission: "some.node" # Player must hold this permission node
  RequiredExam: "Citizen"         # Player must have already PASSED this exam

  # --- Questions ---
  Questions:
    - Question: "Is it ok to grief?"
      Options:
        - "Yes"
        - "No"
        - "Maybe"
        - "I dont know"
      CorrectOption: "B"          # Letter of the correct option: A, B, C, or D
    - Question: "Is it ok to spam?"
      Options:
        - "Yes"
        - "No"
        - "Maybe"
        - "I dont know"
      CorrectOption: "B"
```

### Exam option reference

| Option | Required | Description |
|---|---|---|
| `Command` | one of `Command`/`Commands` | Single console command run when the player passes. |
| `Commands` | one of `Command`/`Commands` | List of console commands run when the player passes. |
| `CommandOnFail` | no | Console command run when the player fails. |
| `NumberOfQuestions` | yes | Number of distinct questions drawn per attempt. Defaults to 1 if missing/zero. |
| `StartTime` | no | World time (0–24000 ticks) the exam opens. |
| `EndTime` | no | World time the exam closes. Equal `StartTime`/`EndTime` = always open. |
| `Price` | no | Fee charged on signup (needs a Vault economy provider). |
| `RequiredRank` | no | Vault permission group the player must belong to. |
| `RequiredPermission` | no | Permission node the player must hold. |
| `RequiredExam` | no | Name of another exam the player must have passed first. |
| `Questions` | yes | List of question maps (`Question`, `Options`, `CorrectOption`). |

**Command placeholder:** `$PlayerName` is replaced with the player's name in any command (single, list, or fail command). Commands run from the **console**, so they have full permissions.

**Reward time window:** `StartTime`/`EndTime` are evaluated against `world.getFullTime() % 24000`. As a rough guide, `0` ≈ 6:00 in‑game, `6000` ≈ noon, `12000` ≈ dusk, `18000` ≈ midnight.

> ⚠️ **Legacy formats are not supported.** Exams that use the old `RankName` property (instead of group commands) or the old map‑style `Questions` section will log a warning and refuse to run. To migrate: back up `exams.yml`, delete it, let the plugin regenerate the example file, and convert your exams to the format above.

---

## Exam Signs

An exam sign is a **wall sign** (any wood type, plus crimson/warped) with:

| Line | Content |
|---|---|
| 1 | `Exam` (case‑insensitive; required to register as an exam sign) |
| 2 | Anything (e.g. `In`) — decorative |
| 3 | The **exact exam name** as defined in `exams.yml` |
| 4 | Anything — decorative |

When placed, the plugin normalizes the sign (rewriting line 1 to `Exam` and line 3 to the canonical exam name, preserving your color codes). Color codes (`&`) are supported and stripped when matching exam names.

- **First right‑click:** signs the player up (runs prerequisite checks and charges any fee).
- **Second right‑click:** starts the exam and opens the first question dialog, or — if the exam is closed — tells the player when it opens.
- **Right‑click while in progress:** re‑opens the current question's dialog (handy if the player closed it). After the time window closes mid‑exam, the next click finalizes and grades the attempt.

Answers are chosen by clicking buttons in the dialog; closing the dialog simply pauses the attempt, and the player's progress is preserved until they click the sign again.

Only players with `exams.place` (or OPs) can create exam signs; unauthorized placements are cancelled and the sign drops as an item.

---

## Commands

Base command: `/exams` (alias `/exam`).

| Command | Permission | Description |
|---|---|---|
| `/exams` | — | Show plugin info and exam count. |
| `/exams help` | `exams.help` | List available commands (filtered by permission). |
| `/exams list` | `exams.list` | List all defined exams. |
| `/exams info <exam>` | `exams.info` | Show details for an exam. *(Currently a stub.)* |
| `/exams reload` | `exams.reload` | Reload `config.yml` and `exams.yml`. |
| `/exams clean` | `exams.clean` | Remove stale/expired student records. |
| `/exams reset <player>` | `exams.reset` | Reset a player's exam state and cooldown. |
| `/exams test <exam>` | `exams.test` | Validate/run an exam for yourself (testing). |
| `/exams studentinfo <player>` | `exams.studentinfo` | Show a player's exam status and passed exams. |

`reload` and `clean` may also be run from the server console.

> **Answering questions** is no longer a command — players answer by clicking buttons in the exam dialog that opens when they take an exam. The old `/exams a|b|c|d` commands have been removed.

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `exams.*` | OP | Grants the bundled child nodes below. |
| `exams.student` | everyone | Take exams (answer questions). |
| `exams.place` | OP | Place exam signs. |
| `exams.list` | everyone | See the list of exams. |
| `exams.info` | everyone | See exam details. |
| `exams.help` | everyone | View the help command. |
| `exams.reload` | OP | Reload configuration. |
| `exams.clean` | OP | Clean up expired student data. |
| `exams.reset` | OP | Reset another player's exam time/state. |
| `exams.nocooldown` | OP | Bypass the exam re‑attempt cooldown. |

> `exams.test` and `exams.studentinfo` are checked in code but are not declared as default nodes in `plugin.yml`; grant them explicitly (or use OP) to use those commands.

---

## Student Data (`students.yml`)

Per‑player exam state is persisted automatically. Each player entry tracks:

- `Exam` — the exam currently being taken (or last signed up for)
- `ExamProgressIndex` — current question position (`-1` = signed up but not started)
- `ExamQuestionIndices` — the randomly selected question indices for this attempt
- `ExamQuestion`, `ExamQuestionOptions`, `ExamCorrectOption` — the active question state
- `ExamCorrectAnswers` — running count of correct answers
- `LastExamTime` — timestamp of the last attempt (used for cooldown/cleanup)
- `PassedExams` — list of exams the player has passed

You normally don't edit this file by hand. Use `/exams reset <player>` to clear a player's state and `/exams clean` to purge stale records.

---

## Building From Source

This is a standard Maven project.

```sh
mvn -B package --file pom.xml
```

The built jar is written to `target/*.jar`. Dependencies (`paper-api`, `VaultAPI`) are `provided` scope and pulled from the PaperMC and JitPack repositories. Requires JDK 21.

### Continuous Integration

`.github/workflows/build.yml` builds with JDK 21 on every push to `master` (and on manual dispatch), uploads the jar as an artifact, and publishes a GitHub Release tagged `build-<run number>` with auto‑generated release notes.

---

## Project Structure

```
ExamsReloaded/
├── pom.xml                              # Maven build (Paper 1.21.8, VaultAPI 1.7, Java 21)
├── .github/workflows/build.yml          # CI: build + release on push to master
└── src/main/
    ├── java/com/dogonfire/exams/
    │   ├── Exams.java                    # Plugin entry point; config load/save, lifecycle
    │   ├── Commands.java                 # /exams command + tab completion
    │   ├── BlockListener.java            # Sign placement & right-click interaction
    │   ├── ExamDialog.java               # Builds & shows the native question dialog (Paper Dialog API)
    │   ├── ExamManager.java              # Exam definitions, question logic, grading, rewards
    │   ├── StudentManager.java           # Per-player exam state in students.yml
    │   ├── PermissionsManager.java       # Vault permission/rank integration
    │   └── Metrics.java                  # bStats metrics
    └── resources/
        ├── plugin.yml                    # Plugin metadata, commands, permissions
        └── roadmap.txt                   # Historical changelog
```

---

## Troubleshooting

- **Plugin won't load** — ensure Vault is installed; it's a hard dependency.
- **"There is no exam called '…'"** — the sign's third line must exactly match an exam key in `exams.yml` (case‑insensitive). Run `/exams list` to see valid names.
- **Exam reward didn't run** — confirm the player's score met `RequiredExamScore`, and that the `Command`/`Commands` are valid console commands. Set `Debug: true` for verbose logs.
- **"This exam is using the old Exams system"** — your exam uses a legacy `Questions`/`RankName` format. Back up and regenerate `exams.yml`, then convert to the current format.
- **Economy/rank features disabled** — these activate only when Vault finds a registered economy provider / permission provider. Check the startup log for "Vault economy found" and "Permission provider found".
- **Players can't escape a cooldown** — grant `exams.nocooldown`, or use `/exams reset <player>`.

---

## Credits

- **Authors:** DogOnFire, Dartanboy (Dartanman)
- Built on the [Paper API](https://papermc.io/) (Dialog API) and [Vault](https://github.com/MilkBowl/Vault).
- A reloaded/modernized continuation of the original *Exams* plugin.
