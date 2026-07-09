# AuthAgain

[![build](https://img.shields.io/github/actions/workflow/status/rluodev/AuthAgain/gradle-build.yml?branch=main&label=build)](https://github.com/rluodev/AuthAgain/actions/workflows/gradle-build.yml)
[![tests](https://img.shields.io/github/actions/workflow/status/rluodev/AuthAgain/gradle-tests.yml?branch=main&label=tests)](https://github.com/rluodev/AuthAgain/actions/workflows/gradle-tests.yml)

A client-side Minecraft Forge 1.20.1 mod that refreshes your Microsoft/Minecraft
session and manages multiple accounts without restarting the game.

## Downloads

- **[Latest release](https://github.com/rluodev/AuthAgain/releases/latest)**
- **[Nightly](https://github.com/rluodev/AuthAgain/releases/tag/nightly)**

Drop the jar into your `mods/` folder.

Note: Nightly is built every night, but is not guaranteed to work so use it at your own risk. Breaking changes may be included
in the nightly build without warning.

### Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17

## Usage

Open the Multiplayer screen and click the "Open AuthAgain" button in the
top-left corner. From there, you can add accounts, reauthenticate, set an account active, or remove accounts.

## Configuration

You can find the configuration file in `config/authagain-client.toml`, and it offers one option:

- `persistAccounts` (default `true`) - save accounts to `config/authagain/accounts.json`. If you would prefer to not save accounts, set this option to `false`.

Accounts are stored as plaintext JSON, which means that anyone who has access to your device and files will have your accounts.

## Building from source

```sh
./gradlew build
```

The mod jar is written to `build/libs/`.

## Running tests

```sh
./gradlew test
```

## License

[MIT](LICENSE)
