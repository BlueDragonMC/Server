package com.bluedragonmc.server

import net.minestom.server.tag.Tag

val HURT_RESISTANT_TIME = Tag.Integer("hurt_resistant_time").defaultValue(0)
val MAX_HURT_RESISTANT_TIME = Tag.Integer("max_hurt_resistant_time").defaultValue(20)
val LAST_DAMAGE = Tag.Float("last_damage").defaultValue(0.0f)