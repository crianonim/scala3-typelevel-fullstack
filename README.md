# scala3-typelevel-fullstack

A full-stack **Scala 3** playground built on the Typelevel ecosystem: a **Cats Effect / http4s** backend,
a **Tyrian** (Elm-architecture) frontend compiled to JavaScript with Scala.js, and a **shared
cross-compiled domain model** that both sides depend on.

It is a mono-repo of several small, self-contained "apps" rather than a single product. Each
app lives in its own package, is routed independently, and usually has its logic in the `common`
module so the exact same code runs on the JVM and in the browser.

- **Scala:** 3.6.4
- **sbt:** 1.10.0
- **Build tool for the frontend bundle:** Parcel 2 + TailwindCSS 3
- **DB:** PostgreSQL via Doobie + HikariCP, configured from `.env.local` (or the optional local Docker DB)

---

## Table of contents

1. [Repository layout](#1-repository-layout)
2. [Prerequisites](#2-prerequisites)
3. [Quick start](#3-quick-start)
4. [The applications](#4-the-applications)
5. [The shared `common` module](#5-the-shared-common-module)
6. [Frontend UI library and styling](#6-frontend-ui-library-and-styling)
7. [How the build pipeline fits together](#7-how-the-build-pipeline-fits-together)
8. [How the database is accessed](#8-how-the-database-is-accessed)
9. [Testing](#9-testing)
10. [Code style and conventions](#10-code-style-and-conventions)
11. [Production build and deployment](#11-production-build-and-deployment)
12. [Editor support](#12-editor-support)
13. [Command cheat sheet](#13-command-cheat-sheet)
14. [Known gaps and gotchas](#14-known-gaps-and-gotchas)

---

## 1. Repository layout

```
.
├── build.sbt                  # The whole sbt build: 3 modules, all dependencies, assembly config
├── CLAUDE.md                  # AI/agent notes about conventions (read this too)
├── notes.txt                  # Scratch notes: psql snippets + the manual dev loop
│
├── common/                    # Cross-compiled (JVM + JS) domain model and business logic
│   └── shared/src/main/scala/com/crianonim/
│       ├── tables/domain/tables.scala        # TableColumns — the only type that crosses the wire
│       ├── roll/                             # Dice rolling (D6, Roll, RollDef, RandomTable)
│       ├── timelines/                        # Timeline / Period / TimePoint / Viewport
│       ├── gentree/Person.scala              # Person — genealogy node
│       ├── screept/                          # The "Screept" mini-language: Parser, Ast, Evaluator
│       ├── dialog/                           # Dialog game model + DialogEngine
│       ├── timelinesquiz/                    # Timeline quiz domain + embedded UK monarch CSV
│       └── forbiddenlands/Character.scala    # Forbidden Lands character generator domain
│
├── server/                    # JVM backend (http4s Ember + Doobie)
│   └── src/main/scala/com/crianonim/tables/
│   ├── Application.scala                  # Main entry point: server, static files, CORS, API wiring
│   ├── Db.scala                           # DbConfig (env/.env.local resolution) + Hikari transactor
│       ├── core/Tables.scala                  # Repository: the only SQL in the codebase
│       └── http/TablesRoutes.scala            # Http4s routes for /tables
│
├── app/                       # Scala.js frontend (Tyrian), bundled by Parcel
│   ├── index.html                           # Parcel entry document
│   ├── index.css                            # Tailwind directives + shadcn design tokens
│   ├── app.js                               # JS shim that boots the Scala.js app
│   ├── package.json                         # npm scripts (start / build-staging / build-prod)
│   ├── tailwind.config.js                   # Tailwind content globs + shadcn token mapping
│   ├── .postcssrc                           # PostCSS plugin config (enables Tailwind)
│   ├── static/img/                          # Image assets
│   ├── dist/                                # Build output — THIS is what the server serves
│   └── src/main/scala/com/crianonim/
│       ├── all/                              # Root Tyrian app + shared shell
│       │   ├── App.scala                     # router, parent Model, init/update/view
│       │   ├── AppRegistry.scala             # Page enum + AppEntry list (single source of truth)
│       │   └── AppShell.scala                # landing page + full-screen app frame (top bar)
│       ├── tables/TablesApp.scala            # App: talks to the backend
│       ├── dnd/DiceRoll.scala               # App: dice roller
│       ├── timelines/TimelinesApp.scala     # App: timeline visualisation
│       ├── gentree/GenTreeApp.scala         # App: family tree
│       ├── screept/ScreeptApp.scala         # App: Screept code editor + runner
│       ├── dialog/DialogApp.scala           # App: dialog player + editor
│       ├── dialoggame/DialogGameApp.scala   # App: full dialog-game editor/player
│       ├── janscape/JanscapeApp.scala       # App: janscape settlement game
│       ├── shadcn/                          # App: shadcn/ui component showcase
│       │   ├── ShadcnShowcase.scala
│       │   ├── Display.scala  Forms.scala  Content.scala  Navigation.scala  Overlays.scala  Icons.scala
│       ├── timelinesquiz/TimelinesQuizApp.scala  # App: year-guessing timeline quiz
│       └── ui/                              # Reusable Tyrian components
│           ├── Button.scala  Card.scala  Input.scala  Modal.scala  Tooltip.scala
│           ├── DateInput.scala  FileInput.scala  SectionTabs.scala
│           └── Preview.scala                # "Preview" app = gallery of the ui/ components
│
├── db/                        # Database: Docker Compose, init SQL, sample data
│   ├── docker-compose.yml                   # PostgreSQL on host port 5444 (optional local option)
│   ├── sql/db.sql                           # Mounted into the container's init directory
│   ├── sql/timelines.sql                    # `timelines` schema + seed data (also init-only)
│   └── timelines.json                       # Sample timeline export (for import testing)
│
├── .env.example               # Template for .env.local — copy it, never commit .env.local
├── .env.local                 # Your DB credentials. Git-ignored. Read at server startup.
│
├── project/                   # sbt build definition and plugins
│   ├── build.properties                     # sbt.version = 1.13.0
│   ├── plugins.sbt                          # sbt-scalajs, sbt-scalajs-crossproject, sbt-assembly, ...
│   └── metals.sbt                           # sbt-bloop (auto-generated, enables Metals)
│
├── run_all.sh                 # One-command tmux dev environment (3 panes)
├── buildandrun.sh             # Full production build -> Docker image -> run on :8080
├── deploy-pi.sh               # Build arm64 image and ship it to a Raspberry Pi over SSH
├── Dockerfile                 # eclipse-temurin:17-jre + the assembled fat JAR
└── add_monarchs.json          # Sample data (not referenced by any code)
```

### The three sbt modules

| Module   | Platform      | SBT project      | Depends on | Purpose |
|----------|---------------|------------------|------------|---------|
| `common` | JVM **and** JS | `coreJVM`, `coreJS` | — | Domain model, parsers, engines, Circe codecs. Compiled twice. |
| `app`    | JS           | `app`            | `coreJS`   | All Tyrian UI code |
| `server` | JVM          | `server`         | `coreJVM`  | http4s server, Doobie repositories |

`common` is a `crossProject(JSPlatform, JVMPlatform) in file("common")`. Its sources live in
`common/shared/src/main/scala` and are compiled once per platform, which is why shared code may only
use cross-platform libraries (Circe, Cats Effect, FastParse, scala-java-time).

> **Important:** when you add a dependency to `common`, use `%%%` (cross-platform) rather than `%%`.

---

## 2. Prerequisites

| Tool       | Version / notes |
|------------|-----------------|
| JDK        | **17** recommended (the Dockerfile uses `eclipse-temurin:17-jre`). JDK 11+ should work. Make sure `JAVA_HOME` points at a JDK, not a JRE. |
| sbt        | 1.10.0 (pinned in `project/build.properties`). The `sbt` launcher will fetch it. |
| Node.js    | 18+ with npm. Needed for Parcel and Tailwind. |
| Docker     | Only if you want the PostgreSQL database (see [section 8](#8-how-the-database-is-accessed)). |
| tmux       | Only if you want to use `./run_all.sh`. |
| PostgreSQL client (`psql`) | Optional, for poking at the database by hand. |

Editor: **Metals** (VS Code / IntelliJ / Metals-enabled editors) works out of the box because
`project/metals.sbt` pulls in `sbt-bloop`. There is a `.vscode/settings.json` that excludes `target`
from the file watcher.

---

## 3. Quick start

### Option A — one command (recommended for day-to-day dev)

```bash
./run_all.sh
```

This creates (or reattaches to) a tmux session named `scala-full-stack` with three panes:

1. `sbt "~app / fastOptJS"` — continuously recompiles the Scala.js frontend
2. `sbt "~server / run"` — starts the http4s server (3 s delay to let sbt warm up)
3. `cd app && npm run start` — Parcel dev server

Then open **<http://localhost:8080>**.

> Note: pane 2 and 3 each invoke `sbt`, and the same run is not shared. SBT will compile the project
> twice; the first run is slow. Subsequent starts use the incremental cache.

### Option B — manual, three terminals

```bash
# Terminal 1 — compile the Scala.js app (leave it running; it watches for changes)
sbt "~app / fastOptJS"

# Terminal 2 — the backend
sbt "server / run"

# Terminal 3 — the frontend bundle
cd app
npm install        # first time only
npm start
```

Then open **<http://localhost:8080>**.

### Which URL to use?

- **<http://localhost:8080>** — the backend, serving the already-built files from `app/dist`.
  This is the one to use for anything that talks to the server (i.e. the **Tables** app), because
  only this origin can resolve `GET /tables`.
- **<http://localhost:1234>** — Parcel's dev server, with hot module replacement. Faster feedback
  on UI changes, but the backend is *not* proxied, so the **Tables** app will not load data there.

Parcel is started with `--dist-dir dist`, so the dev bundle is written to the same directory the
backend serves. That is why `:8080` reflects your latest changes too.

### First-time gotcha

`app/app.js` imports `./target/scala-3.6.4/app-fastopt.js`, and `app/target/` is git-ignored. On a
fresh clone you **must** run `sbt "app / fastOptJS"` (or `~app / fastOptJS`) once *before* `npm start`,
otherwise Parcel will fail to resolve that import.

---

## 4. The applications

Every app lives inside a single Tyrian root app. `app/src/main/scala/com/crianonim/all/App.scala`
owns the `Model`, the `enum Page`, the `router`, `init`, `update` and `subscriptions`; each child app
exposes the same four pure functions — `init`, `update`, `view`, `subscriptions` — and its messages
are lifted into the root `Msg` enum via an `UpdateXxx` wrapper.

`/` is a **landing page** (`all/AppShell.landing`) listing every app as a stacked full-width button.
Selecting one opens it **full-screen**, under a thin top bar (`all/AppShell.appFrame`) with
**Back to Main** on the left and the app name plus a `Version 0.1` placeholder centered. The version
is the single constant `AppShell.Version`. `SectionTabs` is no longer the top-level navigation (it
survives as a component and is still demoed in the Preview gallery).

Adding an app means: create the object, add a `Page` case and a `Model` field, an `UpdateXxx` message
and its `update` branch in `all/App.scala`, and **one `AppEntry` to `AppRegistry`**
(`app/src/main/scala/com/crianonim/all/AppRegistry.scala`). The router, the landing page and the
app-frame title are all derived from that registry, so there is no separate path/tab-label mapping to
keep in sync.

| Route         | App name      | Source                                  | What it does |
|---------------|---------------|-----------------------------------------|--------------|
| `/`           | `Main`        | `all/AppShell.scala`                    | The landing page. Lists every app below as a full-width button. Not an `AppEntry`; rendered directly by `App.view`. |
| `/tables`     | `Tables`      | `tables/TablesApp.scala`                | **The only app that talks to the backend.** Issues `GET /tables` via Tyrian's `Http.send`, parses the response with Circe into `List[TableColumns]`, and renders each column name + data type. Has a "Get Tables Data" button. |
| `/roll`       | `Dice Roll`   | `dnd/DiceRoll.scala`                    | Dice roller. Inputs for dice count, faces and modifier, plus d4/d6/d8/d10/d20/d100 shortcuts. Rolls via `Roll.forCats[IO]` with a `Random` from `scala.util`, renders the individual die results. |
| `/timelines`  | `Timelines`   | `timelines/TimelinesApp.scala`          | Timeline visualiser. Seeded from `TimelinesFromJSON` in `common`; supports a zoomable/pannable viewport, selecting a timeline and fitting the viewport to it, a create-timeline form (point / closed / started periods) and **JSON import/export** of the whole timeline list. |
| `/preview`    | `Preview`     | `ui/Preview.scala`                      | A gallery / living style guide for the hand-rolled components in `com.crianonim.ui` (Button variants, Card variants, Inputs, Tooltip, SectionTabs, Modal). Also useful as a component sandbox. |
| `/screept`    | `Screept`     | `screept/ScreeptApp.scala`              | A code editor and runner for **Screept**, the tiny scripting language implemented in `common/shared/.../screept`. Left pane = source + "Run"; right pane = `PRINT` output and a table of the resulting variable/procedure bindings. Has a syntax-help modal. |
| `/gentree`    | `GenTree`     | `gentree/GenTreeApp.scala`              | Family tree. Add/edit `Person` nodes (name, description, born/died `TimePoint`s, mother/father links), select nodes, export/import the whole tree as JSON. |
| `/dialog`     | `Dialog`      | `dialog/DialogApp.scala`                | Player **and** editor for dialogs built on the `common/dialog` model. Options carry conditions and action trees (`GoBack`, `GoDialog`, `Msg`, `Screept`, `Conditional`, `Block`). Supports `GameDefinition` JSON import/export. |
| `/dialoggame` | `Dialog Game` | `dialoggame/DialogGameApp.scala`        | A larger, two-column re-implementation of the above: inline Screept editing, reordering actions, action-type switching, an environment inspector, a status line, and **localStorage** persistence (auto-save plus named saved games). |
| `/shadcn`     | `Shadcn`      | `shadcn/ShadcnShowcase.scala` (+ 5 files) | A Scala/Tyrian port of the **shadcn/ui** component gallery. Uses the `oklch` CSS variables in `app/index.css` mapped into Tailwind via `tailwind.config.js`. Includes a light/dark toggle that flips a `.dark` class on the gallery root. |
| `/janscape`   | `Janscape`    | `janscape/JanscapeApp.scala`            | A port of the janscape settlement game: menu/play/mine/craft/build/forest screens driven by the `common/.../janscape` model, with effectful rolls in `update` and localStorage autosave. |
| `/timeline-quiz` | `Timeline Quiz` | `timelinesquiz/TimelinesQuizApp.scala` | A port of the standalone Next.js timeline-quiz app. Pick a timeline, then guess which entries cover a random year: single tap, or **Overlap** mode for multi-select + Submit. Instant feedback banner with ~1.2 s auto-advance, a score footer, a faint debug toggle that forces a specific year, and an entries preview page. Pure logic and the embedded `uk_monarchs` CSV live in `common/shared/.../timelinesquiz`. |

### How the apps are related

- `Screept` is the scripting substrate. `Dialog` and `DialogGame` evaluate Screept expressions to
  decide which options are visible and what their text says.
- The domain model in `common` is shared by all of them: `roll` feeds `DiceRoll` and the
  `forbiddenlands` character tables; `timelines` supplies `TimePoint`, which both `Timelines` and
  `GenTree` use for birth/death dates.

### A Tyrian convention worth knowing

Screept evaluation is **effectful** (it needs `Random`), and Tyrian's `view` is pure. Both the Dialog
and DialogGame apps therefore resolve effectful data to plain strings inside `update` (via
`Cmd.Run` with a `Random`) and store the result in the model for `view` to render. Keep that
invariant if you extend them.

---

## 5. The shared `common` module

`common/shared/src/main/scala/com/crianonim/` holds everything that must exist on both sides:

### `roll/` — dice and random tables
- `Roll.scala` — the `Roll[F[_]]` typeclass (`roll`, `eval`, `parseAndRoll`, `parseAndEval`,
  `rollDie`), plus `DiceRoll(diceCount, diceFaces)`, `RollDef`, `RollResult`. A `RollDef` has a
  hand-written Circe `Encoder`/`Decoder` that round-trips through the `"2d6+3"` string form, parsed
  with FastParse. `Roll.forCats` / `Roll.forUnsafe` give you an instance for `IO` or `Id`.
- `D6.scala` — the classic d6, d8, d66 and d666 helpers.
- `RandomTable.scala` — `RandomTable[A]` of `RollRow`s keyed by either a single value or a range
  (D66 style), with `flatten` / `findResult` / `isTableValid`.

### `timelines/` — dates
- `Timeline.scala` — `Timeline(id, name, period)`, the `Period` ADT (`Point`, `Closed`,
  `Started`), the `TimePoint` ADT (`YearOnly`, `YearMonth`, `YearMonthDay`), and `Viewport` with
  the `min`/`max`/`floor`/`ceil` helpers the viewport code uses.
- `TimelinesFromJSON.scala` — the hard-coded seed list used by the Timelines app.

### `screept/` — the scripting language
- `Ast.scala` — `Statement`, `Expression`, `Value` (`NumberValue`, `TextValue`, `FuncValue`),
  `Environment`.
- `Parser.scala` — FastParse-based parser (`parseStatement`, `parseExpression`).
- `Evaluator.scala` — the interpreter, in `F[_]: Monad: Random`.
- `Screept.scala` — the public facade: `parse`, `run`, `eval`, `execute`.
- `ScreeptPrinter.scala` — pretty-printing back to source.
- Also ships a **VS Code TextMate grammar** (`screept.tmLanguage.json`, plus
  `screept/package.json` and `language-configuration.json`) if you want syntax highlighting in
  `.tmLanguage.json` / a `screept` folder.

### `dialog/` — dialog games
- `Dialog.scala` — `GameDefinition`, `Dialog`, `Option`, the `DialogAction` ADT and its Circe
  codecs (note: the JSON type tags are `"go back"`, `"go_dialog"`, … — read the file, don't guess).
- `DialogEngine.scala` — a Scala port of the TypeScript `@crianonim/dialog` engine. The key
  divergence from the original: Screept evaluation is effectful here, so every text/condition/action
  evaluation is `EitherT[F, EvaluationError, _]`, while navigation and lookup helpers stay pure.

### `tables/domain/tables.scala`
```scala
package com.crianonim.tables.domain
object tables:
  case class TableColumns(tableName: String, columnName: String, dataType: String)
```
This is the *only* type that is serialised between server and browser. Because both sides use
`io.circe.generic.auto`, the field names in the JSON are exactly these three.

### `forbiddenlands/Character.scala`
`Attributes`, `Proffesion` and `Kin` enums with `RandomTable`s keyed on d66 ranges — an example of
using the `roll` package to model a tabletop character generator. It is currently domain-only; there
is no app for it.

### `gentree/Person.scala`
`Person(id, name, description, born: TimePoint, died: Option[TimePoint], motherId, fatherId)` with
semiauto Circe codecs.

---

## 6. Frontend UI library and styling

### Prefer `com.crianonim.ui` over raw HTML

`app/src/main/scala/com/crianonim/ui/` is the hand-rolled component kit. When building UI, reach for
these before writing `Html.button` or similar:

| Component      | Notes |
|----------------|-------|
| `Button`       | `primary` / `secondary` / … with a `Size` (`Small`, `Large`, …). |
| `Card`         | Variants for the card visual style. |
| `Input`        | `Input.interactive(value, msg, type_)` for a controlled input. |
| `DateInput`    | Date/time entry built on the `TimePoint` ADT. |
| `FileInput`    | File picker, used by the JSON import flows. |
| `Modal`        | `Modal.withTitle(visible, onClose, title, size)(content)`. |
| `Tooltip`      | Wraps an element and shows a tooltip on hover. |
| `SectionTabs`  | A horizontal tab strip (`TabItem(id, label)`, `activeTabId`, `onTabClick`). No longer the top-level navigation; the app shell lives in `all/AppShell.scala`. |
| `Preview`      | The gallery app itself; also a handy reference for how each component is meant to be used. |

Other house rules worth respecting:
- `Html.text("...")` returns a bare string, not an `Html[A]`. Always wrap it in an element:
  `Html.div()(Html.text("..."))` — or just use the `text("...")` / `"..."` shorthand Tyrian provides
  inside an element.

### Tailwind + shadcn tokens

- `app/index.css` contains the three `@tailwind` directives plus the full shadcn **oklch** token set
  under `:root` and `.dark`.
- `app/tailwind.config.js` maps those CSS variables into Tailwind's theme (`bg-background`,
  `text-foreground`, `border-border`, `ring-ring`, `bg-primary`, …) and sets
  `content: ["./src/**/*.{html,js,ts,jsx,tsx,scala}"]` — note it scans `.scala` files, which is why
  Tailwind class names are written inline in Tyrian `Html` code.
- `darkMode: "selector"` means dark mode is opted into by adding a `.dark` class to an ancestor, not
  by the `prefers-color-scheme` media query.

---

## 7. How the build pipeline fits together

The important thing to understand is that there are **two separate compilers** and a glue file.

```
  common/shared/src/main/scala          app/src/main/scala
              │                                  │
      sbt: coreJVM                      sbt: app  (Scala.js)
      sbt: coreJS                              │
              │                                  ▼
              │                    app/target/scala-3.6.4/app-fastopt.js
              │                                  │
              │                                  ▼
              │                     app/app.js  (3-line JS shim)
              │                                  │
              │                                  ▼
              │                        Parcel + Tailwind + PostCSS
              │                                  │
              │                                  ▼
              │                            app/dist/index.html
              │                          app/dist/index.<hash>.js
              │                          app/dist/index.<hash>.css
              │                                  │
              │                                  ▼
              │                    sbt "server/run"  (http4s Ember, :8080)
              └──────────────────────────────────>│
                                                 ▼
                                   http4s serves ./app/dist
                                   (staticFiles <+> index.html fallback)
```

Key points:

- **Scala.js module kind is `CommonJSModule`** (set in `build.sbt:60`). `app/app.js` is a plain
  ES module that does `import { AllApp } from "./target/scala-3.6.4/app-fastopt.js"` and calls
  `AllApp.launch("app")`. The `"app"` string must match the `<div id="app">` in `app/index.html`.
- **`app/app.js` hardcodes the Scala version and the `fastopt` suffix.** If you bump `scalaVersion`
  in `build.sbt`, update the path in `app/app.js` too. If you switch to `fullOptJS`, change
  `app-fastopt.js` to `app-opt.js`.
- **The server is also the web server.** `Application.scala:30` creates
  `fileService(FileService.Config("./app/dist"))` and `Application.scala:34-38` adds a fallback that
  serves `./app/dist/index.html` for any unmatched path. The routes are combined with
  `staticFiles <+> fallbackRoute` (Application.scala:42) and wrapped in a permissive CORS policy
  (`withAllowOriginAll`, `Application.scala:44`).
- The server binds `0.0.0.0:8080` and then blocks forever with `IO.never`.

### npm scripts

Run from the `app/` directory:

| Command             | What it does |
|---------------------|--------------|
| `npm start`         | `parcel index.html --no-cache --dist-dir dist --log-level info` — dev server on :1234, output to `dist/`. |
| `npm run build-prod`| `parcel build index.html --dist-dir dist --log-level info` — optimised bundle into `dist/`. |
| `npm run build-staging` | Same, into `dist-staging/`. |

---

## 8. How the database is accessed

The server reaches Postgres through **Doobie + HikariCP**, configured entirely from environment
variables. It is wired up and live: `GET /tables` queries the database and returns JSON.

There are two ways to point it at a database:

- **A hosted/remote Postgres** (e.g. Neon, Supabase, RDS) — put the connection details in
  `.env.local`. This is what the current `.env.local` does.
- **The local Docker Postgres** from `db/` — needs no configuration at all, because it is the
  fallback default. See [8.1](#81-option-a-the-local-docker-database).

### 8.1 Configuration: `.env.local`

The server reads its connection settings at startup, in this order — **first hit wins**:

1. the process environment (so `PGHOST=... sbt "server / run"` works)
2. `.env.local` in the working directory
3. built-in defaults matching `db/docker-compose.yml`

`.env.local` is **git-ignored** (see `.gitignore`); `.env.example` is the committed template. To set
up a new machine:

```bash
cp .env.example .env.local
$EDITOR .env.local
```

The variable names are the **libpq standard ones**, so the same file also works for `psql`, `pg_dump`,
Drizzle/Prisma CLIs and most ORMs:

| Variable            | Required | Default        | Notes |
|---------------------|----------|----------------|-------|
| `PGHOST`            | no       | `localhost`    | Hostname of the server. |
| `PGPORT`            | no       | `5432`, or `5444` when the host is localhost | `5444` is the Docker port mapping; see [8.2](#82-option-a-the-local-docker-database). Hosted providers need the standard `5432`. |
| `PGDATABASE`        | no       | same as `PGUSER` | Matches libpq's behaviour. |
| `PGUSER`            | no       | `docker`       | |
| `PGPASSWORD`        | no       | `docker`       | |
| `PGSSLMODE`         | no       | driver default | Appended to the JDBC URL as `sslmode=`. Set `require` for most hosted providers. |
| `PGCHANNELBINDING`  | no       | driver default | Appended as `channelBinding=`. Neon and other SCRAM-based providers want `require`. |

The file format is plain `.env`: `KEY=value`, `#` comment lines, and optional single or double quotes
around the value (which is how the Neon connection string arrives). Note that `DbConfig.parseEnvFile`
deliberately does **not** strip inline `#` comments, so a password containing `#` survives.

`DbConfig` is a plain case class, and its `toString` is overridden to omit the password, so a stray
`println(cfg)` in a log statement cannot leak it. What you *will* see logged is a safe summary:

```
Querying ep-...neon.tech:5432/neondb (user=neondb_owner)
```

### 8.2 Option A: the local Docker database

`db/docker-compose.yml`:

```yaml
version: '3.1'
services:
  db:
    image: postgres
    restart: always
    volumes:
      - "./sql:/docker-entrypoint-initdb.d"
    environment:
      - "POSTGRES_USER=docker"
      - "POSTGRES_PASSWORD=docker"
    ports:
      - "5444:5432"
```

Start it from the `db/` directory:

```bash
cd db
docker compose up -d
```

That gives you:

| Setting        | Value |
|----------------|-------|
| Host port      | `5444` (mapped to the container's `5432`) |
| Username       | `docker` |
| Password       | `docker` |
| Database       | `docker` — `POSTGRES_DB` is not set, so the image creates a database named after the user, and `DbConfig` defaults `PGDATABASE` to the username to match. |
| `pg_hba` auth  | The `postgres` image defaults to `trust`/`scram` over TCP with the password above; `docker`/`docker` works out of the box. |

`restart: always` means it comes back after a reboot. If port `5444` is already taken, change the host
side of the `ports:` mapping **and** set `PGPORT` in `.env.local` to match.

**No `.env.local` is needed for this path** — `localhost` + `5444` + `docker`/`docker` is exactly the
built-in fallback, so the server connects to this database with zero configuration.

### 8.3 Option B: a hosted database

Everything lives in `.env.local`; nothing else changes. A real example, using Neon (the credentials
are this repo's actual `.env.local`, with the password redacted):

```bash
PGHOST='ep-billowing-bar-b4yei69e-pooler.c-6.us-east-2.aws.neon.tech'
PGDATABASE='neondb'
PGUSER='neondb_owner'
PGPASSWORD='npg_************'
PGSSLMODE='require'
PGCHANNELBINDING='require'
```

Those produce the JDBC URL:

```
jdbc:postgresql://ep-billowing-bar-b4yei69e-pooler.c-6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require&channelBinding=require
```

Two things worth noting about this example:

- The host is a **pooler** endpoint (`-pooler.` in the name), not the direct `-ep-...` endpoint.
  Poolers multiplex many clients over one connection, so they need the standard port `5432` — which is
  what `DbConfig` picks by default for non-localhost hosts. This is why the port is not hardcoded to
  the Docker `5444`.
- `sslmode` and `channelBinding` are appended to the URL as connection properties. They are libpq
  names, not JDBC property names, so `DbConfig` maps them across rather than passing them blindly.

### 8.4 Schema and seed data

`db/sql/` is bind-mounted to `/docker-entrypoint-initdb.d`, which the official Postgres image runs
**only on first initialisation of an empty data directory**. It contains two independent scripts.

#### `db/sql/db.sql` — the legacy `jobs` table

```sql
create table jobs(
    id uuid primary key default gen_random_uuid(),
    company text not null,
    title text not null,
    description text not null,
    externalUrl text not null,
    salaryLo integer,
    salaryHi integer,
    currency text,
    remote boolean,
    location text not null,
    country text
);
```

…plus two seed rows (a "Rock the JVM / Instructor" job and a "Google / Software Engineer" job).
It lands in `public` and **no code reads or writes it** — a leftover from an earlier jobs-board
example. It is left in place rather than deleted, but nothing depends on it.

#### `db/sql/timelines.sql` — the `timelines` schema

This one is the real schema. It deliberately does **not** use `public`; everything lives under a
`timelines` schema so the domain data is namespaced away from Postgres defaults.

It models the two sealed traits in
`common/shared/src/main/scala/com/crianonim/timelines/Timeline.scala`:

```scala
Period    = Point(TimePoint) | Closed(TimePoint, TimePoint) | Started(TimePoint)
TimePoint = YearOnly(year) | YearMonth(year, month) | YearMonthDay(year, month, day)
```

```sql
create type timelines.period_kind as enum ('point', 'closed', 'started');
create type timelines.precision   as enum ('year', 'month', 'day');

create table timelines.timeline (
  id               text                   not null,
  name             text                   not null,
  period_kind      timelines.period_kind  not null,
  start_year       integer                not null,
  start_month      integer,
  start_day        integer,
  start_precision  timelines.precision    not null,
  end_year         integer,
  end_month        integer,
  end_day          integer,
  end_precision    timelines.precision,
  constraint timeline_pkey primary key (id)
  -- …plus seven CHECK constraints, see below
);
```

The two design decisions worth understanding:

1. **Precision is an explicit column, not inferred from NULLs.** `TimePoint` is a sealed trait, so
   `YearOnly(2010)` and `YearMonth(2010, 1)` are *different variants* that would otherwise occupy
   identical rows. The `*_precision` columns keep them distinguishable, and the check constraints
   enforce that the month/day NULLs agree with the declared precision. This mirrors
   `TimePoint.timePointFloorDate` / `timePointCeilDate` on the Scala side, where a `YearOnly` spans a
   whole year and a `YearMonthDay` spans a single day.
2. **`period_kind` decides whether an end exists.** `point` is a single moment and `started` is
   open-ended, so both must have no end columns at all; `closed` must have them.

The constraints encode the ADT's invariants, so bad data cannot be inserted at all:

| Constraint | Rejects |
| --- | --- |
| `year_precision_has_no_month_or_day` | `start_precision='year'` with a month or day set |
| `month_precision_is_explicit` | `precision='month'` without a month, or `'day'` without month+day |
| `only_closed_has_an_end` | any end component on a `point` or `started` row |
| `closed_must_have_an_end` | a `closed` row missing its end |
| `period_not_backwards` | a `closed` period whose end precedes its start |
| `month_in_range` / `day_in_range` | month outside 1–12, day outside 1–31 |
| `timeline_pkey` | duplicate ids |

The file then seeds the five rows from `db/timelines.json` with `on conflict (id) do nothing`, and
finishes with a `SELECT` so running the file shows you the result. Every statement is guarded
(`if not exists`, `DO` blocks, `on conflict`), so **re-running it is a no-op rather than an error**.

> **Note on seed data:** `db/timelines.json` and `Timeline.examples` disagree on two entries —
> `002` (`2023-08-07` vs `2024-07-08`) and `003` (`1999-10` vs `1999-09`). The SQL seeds from
> `db/timelines.json`, which matches `TimelinesFromJSON`, i.e. the data the Timelines app actually
> renders. The `005` id gap is present upstream and preserved.

> **Note on migrations:** there is no migration tool here — no Flyway, no Liquibase, no
> `sbt-migration`. Both files are plain init scripts that only apply to the local Docker option, and
> only when the data directory is empty. A hosted database needs the SQL applied by hand:
>
> ```bash
> set -a && . ./.env.local && set +a
> psql -v ON_ERROR_STOP=1 -f db/sql/timelines.sql
> ```
>
> To re-apply locally, wipe the volume: `docker compose down -v && docker compose up -d`.

### 8.5 The connection pool (Doobie + HikariCP)

`server/src/main/scala/com/crianonim/tables/Db.scala`:

```scala
def transactor(cfg: DbConfig): Resource[IO, HikariTransactor[IO]] =
  ExecutionContexts.fixedThreadPool[IO](32).flatMap { ec =>
    HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      cfg.url,
      cfg.user,
      cfg.password,
      ec
    )
  }
```

Points to note:

- It returns a `Resource`, so the pool and its 32-thread compute block are acquired and released as
  a scoped resource — the right shape for a long-running server. There is exactly one such
  construction site, shared by the server and the playground, so they cannot drift apart.
- `ExecutionContexts.fixedThreadPool[IO](32)` is Doobie's `ConnectionIO` blocking pool. **Doobie must
  not run its blocking JDBC work on the Cats Effect CPU pool**, and this is what keeps that promise.
- `HikariTransactor.newHikariTransactor` builds a HikariCP pool with sensible defaults
  (10 connections) and gives you a `Transactor[IO]` that combines the connection pool, the blocking
  EC, and the strategy.

**Hikari connects lazily**, so a bad host or a revoked password would otherwise only surface on the
first request — as a 500 for the user, with no clue in the startup log. `Db.check` closes that gap by
round-tripping one trivial query:

```scala
def check(tx: Transactor[IO]): IO[Unit] =
  sql"select 1".query[Int].unique.transact(tx).void
```

### 8.6 The repository layer

`server/src/main/scala/com/crianonim/tables/core/Tables.scala`:

```scala
trait Tables[F[_]]:
  def all: F[List[TableColumns]]

class TablesLive[F[_]: Concurrent] private (transactor: Transactor[F]) extends Tables[F]:
  override def all: F[List[TableColumns]] =
    sql"""
      Select table_name,column_name,data_type
        from information_schema.columns
       where table_schema not in ('pg_catalog', 'information_schema')
         and table_schema not like 'pg_toast%'
       order by table_schema, table_name, ordinal_position
    """.query[TableColumns]
      .stream.transact(transactor).compile.toList
```

This is the entire data layer, and it is a nice, honest example of Doobie style:

- `sql"..."` is Doobie's `Fragment` interpolator — **not** string interpolation, so it is safe.
- The query returns one row per column of every table in every **non-system** schema. It used to
  hardcode `table_schema = 'public'`, which hid the `timelines` schema entirely; excluding
  `pg_catalog` / `information_schema` / `pg_toast%` is the standard way to get "the user's tables"
  out of `information_schema`.
- `.query[TableColumns]` derives the `Read[TableColumns]` automatically by matching the three
  selected columns positionally against the case class's three fields.
- `.stream` fetches rows lazily, `.transact(transactor)` runs it, `.compile.toList` forces it into a
  `List`.
- The trait is parameterised by `F[_]`, so you could instantiate it with `IO` in production and a
  stub in tests.

The companion offers both constructors:

```scala
object TablesLive:
  def make[F[_]: Concurrent](postgres: Transactor[F]): F[TablesLive[F]]     // lifted construction
  def resource[F[_]: Concurrent](postgres: Transactor[F]): Resource[F, TablesLive[F]] // scoped
```

### 8.7 The HTTP layer

`server/src/main/scala/com/crianonim/tables/http/TablesRoutes.scala`:

```scala
class TablesRoutes[F[_]: Concurrent] private (tables: Tables[F]) extends Http4sDsl[F]:
  private val prefix = "/tables"

  private val getAllRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root =>
    tables.all.flatMap(jobs => Ok(jobs))
  }

  val routes: HttpRoutes[F] = Router(prefix -> getAllRoute)
```

- `Http4sDsl` is the type-safe DSL (`GET -> Root` rather than a raw `GET` + string matcher).
- `Ok(jobs)` needs `EntityEncoder[F, List[TableColumns]]`; that comes from
  `org.http4s.circe.CirceEntityCodec.*`, and the case class gets its `Encoder` from
  `io.circe.generic.auto.*`. **Both imports are load-bearing** — remove either and the route stops
  compiling.
- The result is JSON like:
  `[{ "tableName": "jobs", "columnName": "id", "dataType": "uuid" }, ...]`

The frontend decodes exactly that, with a `Decoder[Msg]` that turns the body into
`Msg.LoadJobs(Left(error))` or `Msg.LoadJobs(Right(list))` (`tables/TablesApp.scala:23-34`), and
`msg.jobs` is an `Either[String, List[TableColumns]]` rendered as either the column list or an error
message.

### 8.8 How the API is wired into the server

`Application.scala` composes the API with the static file service:

```scala
val api: Resource[IO, HttpRoutes[IO]] =
  DbConfig.load().toResource
    .flatMap(Db.transactor(_))
    .evalMap(tx => Db.check(tx).as(tx))
    .flatMap { tx =>
      for
        tables <- TablesLive.resource[IO](tx)
        routes <- TablesRoutes.resource[IO](tables)
      yield routes.routes
    }
    .handleErrorWith { e =>
      Resource.eval(
        IO.println(s"WARNING: Postgres unavailable, /tables is disabled -> ...")
      ).as(HttpRoutes.empty[IO])
    }
```

Two deliberate decisions here:

**Route order matters.** `api` is composed *before* `web` in `makeServer`:

```scala
.withHttpApp(corsPolicy(api <+> web).orNotFound)
```

`web`'s fallback returns `index.html` for *any* unmatched path, so if the API were second, `/tables`
would never be reached. If you ever get `text/html` back from `/tables`, this is why.

**A database outage does not take down the other apps.** `.handleErrorWith` degrades to
`HttpRoutes.empty` and logs a loud warning instead of aborting startup. The SPA keeps working, and
only the Tables app is affected. You can see both paths in action:

```
# healthy
Crianonim Server ready. Test localhost:8080/tables.
$ curl -i localhost:8080/tables
HTTP/1.1 200 OK
Content-Type: application/json
[]

# unreachable database
WARNING: Postgres unavailable, /tables is disabled -> Connection to 127.0.0.1:59999 refused. ...
Crianonim Server ready. Test localhost:8080/tables.
$ curl -i localhost:8080/tables
HTTP/1.1 200 OK
Content-Type: text/html          # the SPA fallback, i.e. the app shows a parse error
```

### 8.9 Poking at the database by hand

From `notes.txt`:

```bash
# The compose file's service is named "db", so the container is "db-db-1" (compose v2 prefixes the project)
docker exec -it db-db-1 psql -U docker
```

Or connect over TCP. Because `.env.local` uses the libpq variable names, `psql` picks up whichever
database the server is configured for with no extra arguments:

```bash
psql                 # local Docker: localhost:5444 as docker/docker
psql postgresql://docker:docker@localhost:5444/docker
```

For the hosted option, just make sure the variables are exported first — `psql` reads `PGHOST`,
`PGPORT`, `PGDATABASE`, `PGUSER` and `PGPASSWORD` natively:

```bash
set -a && . ./.env.local && set +a
psql
```

Useful introspection queries (also in `notes.txt`):

```sql
-- every base table
select * from information_schema.tables
 WHERE table_type = 'BASE TABLE'
   AND table_schema NOT IN ('pg_catalog', 'information_schema');

-- psql-specific
select * from pg_catalog.pg_tables where schemaname = 'timelines';

-- the seed data
select * from timelines.timeline order by id;
```

**Reading the DB from Scala without the server** — `Tables.scala` contains a standalone
`TablesPlayground` `IOApp` that resolves the same configuration, probes the connection, and prints the
column list:

```bash
sbt "server / runMain com.crianonim.tables.core.TablesPlayground"
```

Expected output against the current `.env.local`:

```
Querying ep-...neon.tech:5432/neondb (user=neondb_owner)
List(TableColumns(timeline,id,text), TableColumns(timeline,name,text), ...)
```

Seeing `timeline`'s columns means three things at once: the credentials resolve, TLS and channel
binding work, **and** `db/sql/timelines.sql` has been applied. The `USER-DEFINED` `data_type` values
you will see for `period_kind`, `start_precision` and `end_precision` are how `information_schema`
reports enum columns.

This is the fastest way to confirm your configuration without starting the HTTP server.

### 8.10 Summary of the data flow

```
  process env  ─┐
  .env.local   ─┼─▶ DbConfig.fromEnv ──▶ DbConfig(url, user, password, ...)
  db/ defaults ─┘         │
                            ▼
                Db.transactor(cfg)     HikariTransactor.newHikariTransactor
                            │          + ExecutionContexts.fixedThreadPool[IO](32)
                            ▼
                     Db.check(tx)       select 1   (fail fast, don't fail lazily)
                            │
                            ▼
                     TablesLive.all     SELECT table_name, column_name, data_type
                             │          FROM   information_schema.columns
                             │          WHERE  table_schema NOT IN ('pg_catalog', …)
                            ▼
                     TablesRoutes       GET /tables ──▶ 200 application/json
                            │
                            ▼
        List[TableColumns]   (common: com.crianonim.tables.domain.tables)
                            │
                            ▼
                     TablesApp (Scala.js) ── circe decode ──▶ rendered rows
```

---

## 9. Testing

Only the `common` module has tests, and they run on **both** platforms because `common` is a
cross-project.

```
common/shared/src/test/scala/com/crianonim/screept/
├── ParserTest.scala        # munit FunSuite — parser shapes and error cases
├── EvaluatorTest.scala     # munit — expression/statement evaluation
└── IntegrationTest.scala   # munit CatsEffectSuite — end-to-end parse-and-execute
```

Framework: **munit** 1.2.4 with `munit-cats-effect` 2.2.0, registered in `build.sbt:31` via
`testFrameworks += new TestFramework("munit.Framework")`.

```bash
sbt test                      # everything
sbt "commonJVM/test"          # JVM only  — fastest loop for these tests
sbt "commonJS/test"           # JS only   (needs Node.js on PATH)
sbt "server/test"             # currently no tests
```

The server module *declares* test dependencies that no test uses yet — ScalaTest 3.2.19,
`doobie-scalatest`, `cats-effect-testing-scalatest`, **Testcontainers** 1.21.4 (core +
`postgresql`) and `logback-classic` (`build.sbt:103`, `:112-115`). The intent is clearly a containerised
Postgres for testing repositories, but `server/src/test` does not exist yet. If you add repository
tests, Testcontainers is already wired up:

```scala
PostgreSQLContainer("postgres")  // no fixed port; use .getJdbcUrl / .getUsername / .getPassword
```

Note that neither `db/sql/db.sql` nor `db/sql/timelines.sql` is **applied** by Testcontainers in that
setup; you'd mount or execute them yourself. `timelines.sql` is self-contained and idempotent, so it
is safe to run against a throwaway container.

**Do not point tests at your `.env.local`.** A test that calls `DbConfig.load()` would read the real
credentials and hit the real database. Testcontainers is the right tool precisely because it gives
each run its own throwaway Postgres. When you add repository tests, build the `DbConfig` from the
container's URL instead of loading the environment.

---

## 10. Code style and conventions

Configured in `.scalafmt.conf`:

```
version = "3.5.9"
align.preset = more
maxColumn = 100
runner.dialect = scala3
```

```bash
sbt scalafmt          # format everything
sbt scalafmtCheck     # check only
```

There is **no `sbt-tpolecat`** (it is commented out in `project/plugins.sbt:5`), so the build only
carries one extra flag, `-Xkind-projector` (`build.sbt:7-9`), for kind-polymorphic types.

Conventions visible throughout the codebase:
- Scala 3 indentation syntax (`:` for enums, significant indentation) — no braces.
- Tyrian apps are plain `object`s with `init` / `update` / `view` / `subscriptions`; models are
  nested `case class`es and messages are nested `enum Msg`.
- Prefer the `com.crianonim.ui` components over raw HTML; wrap bare strings in an element.
- Shared code in `common` must stay platform-agnostic and use `%%%` for dependencies.

---

## 11. Production build and deployment

### `buildandrun.sh` — build everything and run in Docker

```bash
./buildandrun.sh
```

which is:

1. `sbt "app / fastOptJS"` — compile Scala.js
2. `cd app && rm -rf .parcel-cache && rm dist/* && npm install && npm run build-prod` — fresh bundle
3. `sbt "server / assembly"` — fat JAR at `server/target/scala-3.6.4/server-assembly-1.0.1.jar`
4. `docker build --no-cache . -t scalafullstack`
5. `docker run -d -p 8080:8080 scalafullstack`

### The `Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY . .
ADD server/target/scala-3.6.4/server-assembly-1.0.1.jar .
ENTRYPOINT ["java", "-jar", "server-assembly-1.0.1.jar"]
EXPOSE 8080
```

The JAR path embeds both the Scala version (`3.6.4`) and `ThisBuild / version` (`1.0.1` from
`build.sbt:1`), so **bumping either breaks the Docker build until the Dockerfile is updated**.

Because the server serves `./app/dist`, the `dist/` directory must be in the build context — that is
why `buildandrun.sh` builds the frontend *before* the image. There is no `.dockerignore`, so the
whole build context (including `node_modules/`, `target/` and `dist/`) is shipped to the daemon.

The assembly merge strategy (`build.sbt:80-86`) discards `META-INF` and `module-info.class` to avoid
the usual fat-JAR conflicts.

### `deploy-pi.sh` — build for a Raspberry Pi and ship over SSH

```bash
./deploy-pi.sh [ssh-host]      # default: crianonim@raspberry5.local
```

It does a preflight `ssh <host> docker info` (failing fast with a checklist if key auth, SSH or
docker-group membership is wrong), runs the same frontend + `assembly` build, cross-builds the image
with `docker buildx build --platform linux/arm64 --load`, then `docker save | ssh <host> docker load`
and (re)starts the container on port 8080. No registry required. Override the architecture with
`PLATFORM=linux/arm/v7 ./deploy-pi.sh` for older 32-bit Pis.

### Ports summary

| Port | Process | What |
|------|---------|------|
| `8080` | http4s (JVM) | Serves the built SPA from `app/dist` and any API routes. **The URL to bookmark.** |
| `1234` | Parcel dev server | Hot-reloading frontend; no backend proxy. |
| `5444` | PostgreSQL (Docker) | The optional local dev database. |

---

## 12. Editor support

`project/metals.sbt` adds `sbt-bloop` 2.0.19, so running `bloop install` (or just opening in Metals)
gives you a working IDE with no extra configuration. `project/build.properties` pins sbt 1.10.0 for
the root build; `app/project/build.properties` pins 1.11.5 for a standalone sbt shell inside `app/`.

For a good experience, open the **repository root** (not `app/`) so Metals sees all three modules.

The Screept package ships a TextMate grammar if you edit `.tmLanguage.json` / `screept` files:
`common/shared/src/main/scala/com/crianonim/screept/screept.tmLanguage.json` and
`.../screept/screept/` (a `package.json` + `syntaxes/` directory, i.e. a minimal VS Code extension
layout).

---

## 13. Command cheat sheet

```bash
# --- frontend ---
sbt "app / fastOptJS"        # compile Scala.js (dev)
sbt "~app / fastOptJS"       # ...and keep recompiling
sbt "app / fullOptJS"        # optimised (remember to update app/app.js to app-opt.js)
cd app && npm install
cd app && npm start          # parcel dev server -> :1234, writes app/dist
cd app && npm run build-prod # parcel build -> app/dist
cd app && npm run build-staging

# --- backend ---
sbt "server / run"           # http4s on :8080
sbt "server / runMain com.crianonim.tables.core.TablesPlayground"  # one-off DB query
sbt "server / assembly"      # fat JAR
sbt "server / reStart"       # restart the forked JVM app
sbt "~server / reStart"      # ...continuously

# --- tests / format ---
sbt test
sbt "commonJVM/test"
sbt "commonJS/test"
sbt scalafmt
sbt scalafmtCheck

# --- database ---
# --- database config ---
cp .env.example .env.local          # first time only; then edit it
cat .env.local                      # check what the server will see
PGHOST=localhost PGPORT=5444 sbt "server/run"   # one-off override
set -a && . ./.env.local && set +a && psql      # hand the same creds to psql

cd db && docker compose up -d
cd db && docker compose down        # stop (keep data)
cd db && docker compose down -v     # stop and WIPE the volume (re-runs db/sql/*.sql next start)
docker exec -it db-db-1 psql -U docker
psql postgresql://docker:docker@localhost:5444/docker

# --- scripts ---
./run_all.sh                 # tmux dev environment
./buildandrun.sh             # full build -> docker image -> run on :8080
./deploy-pi.sh [host]        # cross-build arm64 and ship to a Pi
```

---

## 14. Known gaps and gotchas

Things that will trip up a new user. None of them are blockers for the frontend; they matter if you
start working on the backend.

1. **`.env.local` is required to match the committed code.** It is git-ignored, so a fresh clone has
   no credentials. Until you run `cp .env.example .env.local`, the server falls back to the local
   Docker defaults and, with no Docker database running, logs
   `WARNING: Postgres unavailable, /tables is disabled` while the other apps work fine. Nothing
   crashes — but `/tables` returns the SPA's `index.html`, so the Tables app shows a parse error.
2. **`.env.local` is a secret.** It holds a live Neon password. It is git-ignored
   (`.gitignore:180-183`), and `!.env.example` keeps the template committable — do not remove that
   negation. If you ever commit the real file, rotate the password; git history will not forget.
3. **No migration tool.** Files in `db/sql/` run only when the Postgres data directory is empty, and
   only for the local Docker option. After editing one you need
   `docker compose down -v && docker compose up -d`. For a hosted database, apply it by hand with
   `psql -v ON_ERROR_STOP=1 -f db/sql/timelines.sql` — that is how the `timelines` schema got onto
   the Neon database. Both scripts are idempotent, so re-applying is safe.
4. **The `jobs` table is unused.** No code reads or writes it, and it is the only thing left in
   `public`. Safe to delete `db/sql/db.sql` if you want `public` to be empty.
5. **`app/app.js` hardcodes `target/scala-3.6.4/app-fastopt.js`.** Bump `scalaVersion` or switch to
   `fullOptJS` and Parcel will not find the module until you update that import.
6. **The version string is duplicated in `Dockerfile`.** `server-assembly-1.0.1.jar` and
   `server/target/scala-3.6.4/...` must be kept in sync with `ThisBuild / version` and `scala3Version`.
7. **No CI.** There is no `.github/` directory, so nothing runs the build or tests on push. There is
   also no `.dockerignore`.
8. **No `server` tests yet**, though ScalaTest, `doobie-scalatest`, `cats-effect-testing` and
   Testcontainers are already declared in `build.sbt`.
9. **Parcel on :1234 has no backend proxy**, so the Tables app can only work through :8080.
10. **`pureconfig` is on the classpath but unused.** It was presumably the original intent for
    configuration; `DbConfig` reads the environment directly instead. Harmless, but it is a
    dependency you could delete.
11. **`DbConfig.load()` needs explicit `()`.** It has a default parameter, and in a select chain
    Scala 3 eta-expands the method value rather than applying it — `DbConfig.load.toResource` is a
    `File => IO[DbConfig]`, not an `IO[DbConfig]`. The compiler error is confusing; the fix is
    `DbConfig.load()`.
12. **Assets that are not wired up:** `app/static/img/*` and `app/css/style.css` are not referenced by
    `index.html` or any Scala source, and `add_monarchs.json` is sample data. `db/timelines.json` is
    also the source `db/sql/timelines.sql` seeds from, though the Timelines app only ever reads the
    *format*, since it exports to that filename. `moment` is in `package.json` but is not imported
    from Scala.
13. **`timelines.timeline` is not read by any code yet.** The schema, constraints and seed data are
    real, and `/tables` lists the columns, but no Doobie query reads the rows — the Timelines app
    still loads its data from `TimelinesFromJSON` in the frontend. The table is the persistence layer
    waiting for its repository.
14. **`CLAUDE.md` is partly stale** — it lists older dependency versions (http4s 0.23.15, doobie
    RC1, circe 0.14.0, cats-effect 3.6.3, fastparse 3.1.1, munit 1.2.4) and says the DB is on a
    `postgres:latest` container started with plain `docker run`. The authoritative versions are in
    `build.sbt`; DB config is in `.env.local` / `DbConfig`.
