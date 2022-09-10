rootProject.name = "Server"

pluginManagement {
    includeBuild("build-logic")
}

include("common")

include("games:arenapvp")
include("games:bedwars")
include("games:fastfall")
include("games:infection")
include("games:infinijump")
include("games:lobby")
include("games:pvpmaster")
include("games:skywars")
include("games:teamdeathmatch")
include("games:wackymaze")
