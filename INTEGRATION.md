# Integration Guide
This guide covers how to integrate other server software with [BlueDragonMC/Puffin](https://github.com/BlueDragonMC/Puffin).
## 1: Background
### 1.1: Kubernetes
In production, BlueDragon runs in a [Kubernetes](https://kubernetes.io/) cluster.
* Game servers are controlled by [Agones](https://agones.dev) with a [fleet](https://agones.dev/site/docs/reference/fleet/).
* Proxies are not currently handled by a fleet, however this may change in the future.
## 2: Messaging
### 2.1: Reference - All gRPC Services
| Service name                                                                                                | Purpose                                                              | Implemented By     |
|-------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|:-------------------|
| [agones](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/agones.proto)                       | Interacting with the Agones SDK                                      | Agones SDK         |
| [gs_client](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/gs_client.proto)                 | Client methods which are implemented by each game server             | Game server        |
| [party_svc](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/party_svc.proto)                 | Handling the BlueDragon party system                                 | Puffin             |
| [player_holder](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/player_holder.proto)         | Client methods which are implemented by each proxy and game server   | Game server, Proxy |
| [player_tracker](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/player_tracker.proto)       | Tracking the instance, proxy, and game server of every player        | Puffin             |
| [queue](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/queue.proto)                         | Adding and removing players from the queue                           | Puffin             |
| [server_tracking](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/server_tracking.proto)     | Updating available instances on each server and their current states | Puffin             |
| [service_discovery](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/service_discovery.proto) | Finding an available lobby for a player to join                      | Puffin             |
| [velocity_message](https://github.com/BlueDragonMC/RPC/blob/master/src/main/proto/velocity_message.proto)   | Private messaging                                                    | Puffin             |
### 2.2: Inbound
Inbound messages are messages received from other services in the cluster.

To receive messages, each game server starts its own gRPC server on port 50051. This port is exposed to other services in the cluster.

Every game server's gRPC endpoint should implement all services which have "Game server" in the "Implemented By" column of the table in section 2.1.

*See the [BlueDragonMC/RPC](https://github.com/BlueDragonMC/RPC/) repository for all of BlueDragon's proto files.*
### 2.3: Outbound
Outbound messages are messages sent from a game server to other services in the cluster.

To send messages, a gRPC client should be set up on each game server.

Game servers can send messages to (and expect RPC responses from) all services which have "Puffin" in the "Implemented By" column of the table in section 2.1.
The IP address of Puffin can be found because it is exposed as a Kubernetes service. The DNS name "puffin" should resolve to an existing server address. Puffin uses port 50051 for its gRPC server.
## 3: Database
### 3.1: Database Internals
BlueDragon runs [MongoDB](https://www.mongodb.com/), a document database with no rigid schema.
In the Kubernetes cluster, it is made available under the `mongo` [service](https://kubernetes.io/docs/concepts/services-networking/service/), so the hostname `mongo` should resolve to a valid MongoDB server address.
The service should be available on port 27017, the default MongoDB port.
### 3.2: Player Documents
Each player has their own document in the `players` collection of the database with the following fields:
* `_id`: The UUID of the player, represented as a string.
* `username`: The username of the player. Updated whenever the name changes.
* `coins`: The number of coins the player has.
* `experience`: The amount of experience the player has.
* `punishments`: A list of punishments that the player has. Punishments are not removed from the list when revoked or expired.
  * `type`: A string from the following list: `BAN`, `MUTE`.
  * `id`: A UUID (represented as a string) that uniquely identifies the punishment.
  * `issuedAt`: The time the punishment was issued, represented as a Unix timestamp (milliseconds after Jan 01, 1970, 00:00:00 GMT)
  * `expiresAt`: The time the punishment will expire/has expired, represented as a Unix timestamp (milliseconds after Jan 01, 1970, 00:00:00 GMT)
  * `moderator`: The UUID of the player which enacted the punishment.
  * `reason`: A short string description of why the punishment was enacted, provided by the `moderator`.
  * `active`: A boolean representing whether the punishment is currently effective. If set to `false`, the expiration should not be considered. If set to `true`, the expiration should still be checked. Expired punishments will never be active, even though this field may be set to `true`.
* `achievements`: TBD - Not implemented
* `cosmetics`: A list of cosmetics which the player owns. Non-equipped cosmetics are included in the list.
  * `id`: A string identifier for the cosmetic
  * `equipped`: A boolean representing whether the player has the cosmetic equipped or not.
### 3.3: Database Behavior
* Every time a player log in to a game server, their player document should be fetched using their UUID.
  * If their username does not match the name in the document, it should be updated to reflect the username change.
* Map data should be lazily fetched for a map when it is loaded.
* Player documents are fetched by other services (mainly Puffin) to look up players' metadata. This is typically just their usernames (UUID <=> username conversion) and name colors for display in chat.
## 4: Server Behavior
### 4.1: Proxy Connection
Every game server is connected to at least one proxy. Connections are registered/handled by the proxies, but player information forwarding must be setup.
The server receives a Velocity forwarding secret using the `PUFFIN_VELOCITY_SECRET` environment variable. If this is present, Velocity modern forwarding should be enabled using the provided secret. If not, Mojang authentication (online mode) should be enabled.
### 4.2: Lobbies
Each server has at least one lobby, which is initialized on startup.
### 4.3: Agones Integration
When a server starts up, it should contact its local Agones gRPC or HTTP server and send a "Ready" request.
Periodically, health pings should be sent to the same endpoint. If they are not sent, the server will be shut down due to a health check failure.
### 4.4: World Loading
Worlds are currently stored in the Anvil format and mounted into each game server's container.
The directory structure looks something like this:
```
/
 server/
        worlds/
               game_name/
                         map_name_1/
                         other_map_name/
               other_game_name/
                               map_name_1/
                               other_map_name/
```
### 4.5: World Configuration
Each map can have a `config.yml` file inside the Anvil world folder with a few keys.
All map-specific keys are namespaced under the `world` key.
* `world`: (the parent key)
  * `name`: A display name for the map
  * `description`: A short description of the map, shown to every player at the start of the game.
  * `author`: A string with the names of the map's builders.
  * `spawnpoints`: A list of spawnpoints for the map.
    * Each spawnpoint is an array of numbers with the format: [x, y, z, yaw, pitch]
    * The numbers may be integers or doubles. It is the responsibility of the client to convert the numbers to the correct format (usually Double).
  * `additionalLocations`: Each map or game may define additional locations. This field is a list of lists of coordinates. The coordinates are in the same format as above.