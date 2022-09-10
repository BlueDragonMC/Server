package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.module.GameModule
import net.minestom.server.instance.Instance

abstract class InstanceModule : GameModule() {
    abstract fun getInstance(): Instance
}