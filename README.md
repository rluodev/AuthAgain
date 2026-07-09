# AuthAgain

[![nightly build](https://img.shields.io/github/actions/workflow/status/rluodev/AuthAgain/gradle-build.yml?branch=main&label=nightly%20build)](https://github.com/rluodev/AuthAgain/actions/workflows/gradle-build.yml)
[![tests](https://img.shields.io/github/actions/workflow/status/rluodev/AuthAgain/gradle-tests.yml?branch=main&label=tests)](https://github.com/rluodev/AuthAgain/actions/workflows/gradle-tests.yml)

A client-side Minecraft mod (Forge 1.20.1 and NeoForge 1.21.1) that refreshes your
Microsoft/Minecraft session and manages multiple accounts without restarting the game.

## Downloads

- **[Latest release](https://github.com/rluodev/AuthAgain/releases/latest)**
- **[Nightly](https://github.com/rluodev/AuthAgain/releases/tag/nightly)**

Drop the jar into your `mods/` folder.

Note: Nightly is built every night, but is not guaranteed to work so use it at your own risk. Breaking changes may be included
in the nightly build without warning.

### Requirements

- **Forge:** Minecraft 1.20.1, Forge 47.x, Java 17
- **NeoForge:** Minecraft 1.21.1, NeoForge 21.1.x, Java 21

Use the jar matching your loader: `authagain-forge-*.jar` (1.20.1) or
`authagain-neoforge-*.jar` (1.21.1).

## Usage

Open the Multiplayer screen and click the "Open AuthAgain" button in the
top-left corner. From there, you can add accounts, reauthenticate, set an account active, or remove accounts.

## Configuration

You can find the configuration file in `config/authagain-client.toml`, and it offers one option:

- `persistAccounts` (default `true`) - save accounts to `config/authagain/accounts.json`. If you would prefer to not save accounts, set this option to `false`.

Accounts are stored as plaintext JSON, which means that anyone who has access to your device and files will have your accounts.

## Building from source

Building requires a **JDK 21** toolchain.

```sh
./gradlew chiseledBuildAndCollect
```

This builds every loader/version variant and gathers the jars under
`build/libs/<version>/<loader>/`. To only build a single variant, use
`./gradlew :forge:1.20.1:build` or `./gradlew :neoforge:1.21.1:build`.

## Running tests

To run the tests for every loader/version variant:

```sh
./gradlew chiseledTest
```

To run for only a single variant: `./gradlew :forge:1.20.1:test` or `./gradlew :neoforge:1.21.1:test`.

## Acknowledgements

AuthAgain uses [Stonecutter](https://stonecutter.kikugie.dev/) for
multi-version source processing and [Architectury Loom](https://github.com/architectury/architectury-loom) for loader support.

## License

[MIT](LICENSE)
