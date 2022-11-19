# Server
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

Environment variables:
* `PUFFIN_VELOCITY_SECRET` - Your Velocity proxy forwarding secret (optional). If not specified, Mojang authentication will be enabled.

## Implementation
To learn how to integrate other server software with BlueDragon's systems, see the [Integration Guide](./INTEGRATION.md)