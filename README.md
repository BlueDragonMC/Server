# Server
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/BlueDragonMC/Server/gradle.yml?branch=main&label=Java%20CI%20with%20Gradle&logo=github)
![GitHub last commit](https://img.shields.io/github/last-commit/BlueDragonMC/Server)
![Jitpack CI Status](https://img.shields.io/badge/dynamic/json?color=blue&label=jitpack&query=%24.status&url=https%3A%2F%2Fjitpack.io%2Fapi%2Fbuilds%2Fcom.github.BlueDragonMC%2FServer%2Flatest&logo=gradle)

[![BlueDragon Logo](./favicon_64.png)](https://bluedragonmc.com)

BlueDragon's [Minestom](https://minestom.net/) implementation. It currently includes:
- Creating isolated instances for different game types and modes
- A modular system for adding functionality to games
  - This allows for a very high degree of code reusability and simplicity, and makes rapid prototyping of games very quick and easy.
- System for handling player punishments
- Database support linked to every `Player` using a player provider
- Synchronization with other servers using gRPC messaging and a Mongo database
- Routing players to the correct instance when they join
- Separated, per-instance chat and tablist functionality
- Basic commands

Minestom is a Minecraft server library targeted at developers. Their wiki is available [here](https://wiki.minestom.net).

## Usage
Build with `./gradlew build` and run the JAR created at `build/libs/Server-x.x.x-all.jar`.
Requires Java 17 or higher.

## Development
This can be built as a docker container with the following command:
```shell
$ DOCKER_BUILDKIT=1 docker build -t bluedragonmc/server:testing .
```
This uses the `Dockerfile` in the current directory to build an image with the version string `"testing"`.
*Note: A game named `Lobby` must be present for the server to run!*

Environment variables:
* `PUFFIN_VELOCITY_SECRET` - Your Velocity proxy forwarding secret (optional). If not specified, Mojang authentication will be enabled.
* `BLUEDRAGON_AGONES_HEALTHCHECK_INTERVAL_MS` - The amount of time in between Agones healthcheck pings, in milliseconds.
* `BLUEDRAGON_AGONES_RESERVATION_TIME_MS` - The amount of time in between Agones server reservations, in milliseconds.
* `BLUEDRAGON_AGONES_DISABLED` - Disables Agones integration if set to any value.
* `BLUEDRAGON_ENV_TYPE` - Set to "DEV" to enable development mode.
* `BLUEDRAGON_QUEUE_TYPE` - Set to "IPC" to use Puffin or "TEST" for the `TestQueue`. If not present, a default value is inferred.
* `BLUEDRAGON_MONGO_HOSTNAME` - The hostname used to connect to MongoDB.
* `BLUEDRAGON_PUFFIN_HOSTNAME` - The hostname used to connect to Puffin.
* `BLUEDRAGON_LUCKPERMS_HOSTNAME` - The hostname used to connect to LuckPerms.
* `HOSTNAME` - Used to determine the server name. Provided by default in Docker or Kubernetes environments.
* `SERVER_INSTANCE_MIN_INACTIVE_TIME` - The amount of time that an instance must be inactive before it is cleaned up.
* `SERVER_INSTANCE_CLEANUP_PERIOD` - The amount of time in between instance cleanup tasks.

## Implementation
To learn how to integrate other server software with BlueDragon's systems, see the [Integration Guide](./INTEGRATION.md)

## Creating a Game
To learn how to create a game using this library, see our [ExampleGame](https://github.com/BlueDragonMC/ExampleGame/blob/main/README.md) repository. It has guides and documentation for creating a simple game.

## Project Structure
The project contains a `common` subproject, which is used by all games as an API to compile against.
This subproject also contains many useful game modules that most games use.

The `testing` subproject contains testing fixtures and utilities for running automated tests.

The main subproject (in `src/*`) contains all the code necessary to start the server, load plugins,
and connect with external services like databases and IPC/messaging systems.
It also contains all the global commands and translations.