# ❄ Modrinth Manager

An in-game Modrinth browser for Fabric. Press **M** and search, preview and
one-click install mods, shaderpacks and texture packs without leaving the
world.

## Features

- Dark, red-and-white winter-themed ClickGUI with a subtle snowfall overlay
- Three categories: **Mods**, **ShaderPacks**, **Texture Packs**
- Real [Modrinth API v2](https://docs.modrinth.com/api/) integration — live
  search, Minecraft-version filtering, project icons and descriptions
- One-click install: resolves the best matching version for your running
  Minecraft version and Fabric, then downloads it straight into the correct
  folder (`mods/`, `shaderpacks/`, `resourcepacks/`)
- SHA-1 verified downloads, no overwriting of unrelated existing files,
  no duplicate installs
- Fully asynchronous networking — the game thread never blocks on a
  search or a download
- Responses are cached for a couple of minutes to avoid hammering the API

## Supported versions

Minecraft **1.20.1**, Fabric Loader, Fabric API, Java 17.

## Requirements

- [Fabric Loader](https://fabricmc.net/use/) `>=0.15.0`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 17+

## Installation

1. Install Fabric Loader for Minecraft 1.20.1.
2. Drop `Fabric API` and `modrinth-manager-<version>.jar` into your `mods/`
   folder.
3. Launch the game, press **M** in-world to open the manager.

## Building

The repo has no Gradle wrapper jar committed; CI provisions Gradle itself.
Locally:

```bash
gradle build
```

The output jar lands in `build/libs/`.

## GitHub Actions

Every push to `main`/`master`, every pull request, and manual dispatch
trigger a build; the resulting jar is uploaded as a workflow artifact.
Pushing a tag like `v1.0.0` additionally cuts a GitHub Release with the jar
attached.

## Screenshots

_(add screenshots of the ClickGUI here)_

## License

MIT — see [LICENSE](LICENSE).
