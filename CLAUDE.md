# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Scala 3 Typelevel Full-Stack Project

## Project Overview

This is a full-stack Scala 3 application using the Typelevel ecosystem, featuring a functional backend and a Scala.js frontend with shared domain models.

**Version:** 1.0.1
**Scala Version:** 3.6.4

## Architecture

### Three-Tier Structure

1. **Common** (`common/`) - Shared code compiled to both JVM and JS
   - Domain models and core business logic
   - Cross-platform utilities (Character, Tables, Dice Rolling, Timelines)

2. **Server** (`server/`) - Backend HTTP API (JVM)
   - Http4s Ember server
   - Doobie for PostgreSQL database access
   - REST API endpoints

3. **App** (`app/`) - Frontend SPA (Scala.js)
   - Tyrian framework (Elm-like architecture)
   - TailwindCSS for styling
   - Parcel for bundling

## Technology Stack

### Backend (JVM)
- **Cats Effect** 3.6.3 - Functional effects
- **Http4s** 0.23.15 - HTTP server and routing
- **Doobie** 1.0.0-RC1 - Database access
- **Circe** 0.14.0 - JSON serialization
- **HikariCP** - Connection pooling
- **Tsec** 0.4.0 - Security library

### Frontend (Scala.js)
- **Tyrian** 0.14.0 - Elm-like UI framework
- **Circe** 0.14.0 - JSON handling
- **TailwindCSS** 3.4.1 - Utility-first CSS
- **Parcel** 2.1.0 - Zero-config bundler

### Shared
- **Cats Effect** 3.6.3
- **Circe** 0.14.0 - Shared JSON codecs
- **FastParse** 3.1.1 - Parser combinators
- **scala-java-time** 2.5.0 - Cross-platform date/time

## Project Structure

```
.
├── common/               # Cross-compiled shared code
│   └── shared/src/main/scala/com/crianonim/
│       ├── forbiddenlands/  # Character domain
│       ├── tables/          # Tables domain
│       ├── roll/            # Dice rolling logic
│       └── timelines/       # Timeline functionality
│
├── server/               # Backend JVM application
│   └── src/main/scala/com/crianonim/tables/
│       ├── Application.scala    # Main server entry point
│       ├── core/               # Core business logic
│       └── http/               # HTTP routes
│
├── app/                  # Frontend Scala.js application
│   ├── src/main/scala/com/crianonim/
│   │   ├── ui/            # Reusable UI components
│   │   ├── tables/        # Tables app
│   │   ├── dnd/           # D&D dice roller
│   │   ├── timelines/     # Timeline app
│   │   └── all/           # Main app entry
│   ├── index.html
│   ├── package.json
│   └── dist/             # Build output
│
├── db/                   # Database scripts
├── project/              # SBT build configuration
└── build.sbt            # Main build definition
```

## Key Files

### Server Entry Point
- `server/src/main/scala/com/crianonim/tables/Application.scala` - Main server application
  - Configures Ember HTTP server on port 8080
  - Serves static frontend files from `./app/dist`
  - CORS enabled for all origins
  - PostgreSQL connection via HikariCP (currently commented out)
  - SPA routing with fallback to index.html

### Build Configuration
- `build.sbt` - Multi-module SBT build
  - Cross-compilation setup (JVM/JS)
  - Dependency management
  - Assembly configuration for fat JARs

## Running the Project

### Prerequisites
- JDK 11+
- SBT 1.x
- Node.js and npm (for frontend tooling)
- PostgreSQL (if using database features)

### Development Workflow

#### Quick Start: Automated Development Environment
```bash
# Runs all three processes in a tmux session:
# - SBT with auto-compiling Scala.js (~app/fastOptJS)
# - SBT server with hot reload (~server/run)
# - Parcel dev server for frontend (npm start)
./run_all.sh
```

This creates a tmux session named "scala-full-stack" with three panes. The backend serves on `http://localhost:8080`.

#### Manual Development Workflow

**Backend Only:**
```bash
# Run server in development mode
sbt server/run

# Run with auto-reload on changes
sbt "~server/reStart"

# Build fat JAR for deployment
sbt server/assembly
```

**Frontend Only:**
```bash
cd app

# Install dependencies
npm install

# Development with hot reload
npm start

# Production build
npm run build-prod
```

**Scala.js Compilation:**
```bash
# Fast optimization (development)
sbt "app / fastOptJS"

# Watch mode for auto-recompilation
sbt "~app / fastOptJS"

# Full optimization (production)
sbt "app / fullOptJS"
```

**Multi-Module Commands:**
```bash
# Compile all modules
sbt compile

# Compile specific module
sbt server/compile
sbt app/compile
sbt common/compile

# Test all modules
sbt test

# Test specific module
sbt server/test
```

#### Port Configuration
- Backend server: `http://localhost:8080`
- Frontend dev server (Parcel): `http://localhost:1234`
- Database (PostgreSQL): `localhost:5444`

### Database Setup

The application expects PostgreSQL on `localhost:5444`:
- Database: default
- User: docker
- Password: docker

You can use Docker:
```bash
docker run -d \
  -p 5444:5432 \
  -e POSTGRES_USER=docker \
  -e POSTGRES_PASSWORD=docker \
  postgres:latest
```

## Build Artifacts

- `server/target/scala-3.6.4/*.jar` - Server JARs
- `app/target/scala-3.6.4/*.js` - Compiled Scala.js
- `app/dist/` - Frontend bundle (served by backend)

## Development Features

### Cross-Compilation Architecture
The `common` module uses `sbt-crossproject` to compile shared code to both JVM and JavaScript:
- Domain models in `common/shared/src/main/scala` are available to both server (JVM) and app (JS)
- Shared JSON codecs ensure serialization compatibility
- Cross-platform libraries: Circe, Cats Effect, scala-java-time
- Build with `sbt common/compile` to compile both targets

**Important:** When adding dependencies to `common`, use `%%%` instead of `%%` to get cross-platform versions:
```scala
libraryDependencies += "io.circe" %%% "circe-core" % version  // Cross-platform
```

### Hot Reloading
- Frontend: Parcel provides instant HMR for UI changes
- Backend: SBT `~reStart` or `~server/run` for automatic recompilation
- Scala.js: `~app/fastOptJS` for continuous compilation

### Static Type Safety
- End-to-end type safety from database to UI
- Compile-time guarantees for JSON serialization via Circe
- Shared validation logic in the `common` module
- Tyrian's Elm architecture enforces type-safe state updates

## Compiler Options

The project uses `-Xkind-projector` for kind-polymorphism support, enabling advanced type-level programming patterns common in the Typelevel ecosystem.

## Testing

Test dependencies configured:
- ScalaTest 3.2.12
- Cats Effect Testing
- Doobie ScalaTest
- TestContainers for integration tests

Run tests:
```bash
sbt test
sbt server/test
```

## API Endpoints

The server currently:
- Serves static files from `./app/dist`
- Falls back to `index.html` for SPA client-side routing
- Has CORS enabled for cross-origin requests
- Tables API routes (currently commented out in Application.scala:47-49)

## Notes

- The Tables API is currently disabled in `Application.scala` (lines 47-49)
- Database connection pooling is configured but not actively used
- The project includes multiple domain areas: tables, dice rolling, characters, timelines
- Frontend uses moment.js for date handling alongside scala-java-time

## Scripts

### Development Scripts
- `run_all.sh` - Automated tmux-based development environment (runs backend, frontend, and Scala.js compiler in parallel)
- `buildandrun.sh` - Full production build pipeline:
  1. Compiles Scala.js (`app/fastOptJS`)
  2. Builds frontend bundle with Parcel
  3. Creates server fat JAR (`server/assembly`)
  4. Builds Docker image
  5. Runs Docker container on port 8080

### Frontend Scripts (in `app/package.json`)
- `npm start` - Development server with hot reload
- `npm run build-staging` - Staging build
- `npm run build-prod` - Production build

## Code Formatting

Format Scala code with:
```bash
sbt scalafmt           # Format all Scala files
sbt scalafmtCheck      # Check formatting without modifying
```

Configuration in `.scalafmt.conf`:
- Scala 3 dialect
- Max line length: 100 characters
- Alignment preset: more

## Configuration Files

- `.scalafmt.conf` - Scalafmt code formatting rules
- `app/tailwind.config.js` - TailwindCSS configuration
- `app/.postcssrc` - PostCSS configuration for Parcel
- `build.sbt` - Multi-module SBT build with cross-compilation settings
- Html.text does not generate Html.Html[A] - always wrap Html.text("x") in a Html.div() Html.div()(Html.text("x))
- when buidling ui prioritise using existing components from package com.crianonim.ui (ie Button) in favour of inlining raw html (like Html.button)