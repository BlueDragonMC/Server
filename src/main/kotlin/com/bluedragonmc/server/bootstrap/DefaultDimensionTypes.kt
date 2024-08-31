package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object DefaultDimensionTypes : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        // Register the fullbright dimension before the server starts,
        // fixing an issue where clients that haven't received the dimension
        // type get kicked when trying to switch instances: https://github.com/Minestom/Minestom/issues/2229
        CustomGeneratorInstanceModule.getFullbrightDimension()
    }
}
