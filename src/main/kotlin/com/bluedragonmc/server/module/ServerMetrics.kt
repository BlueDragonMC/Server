package com.bluedragonmc.server.module

import com.bluedragonmc.server.module.messaging.MessagingModule
import dev.cubxity.plugins.metrics.api.UnifiedMetricsProvider
import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.data.Metric

object ServerMetrics {

    private val metrics = UnifiedMetricsProvider.get()

    fun initialize() {
        metrics.metricsManager.registerCollection(object : CollectorCollection {
            override val collectors: List<Collector> = listOf(MetricCollector())
        })
    }

    class MetricCollector : Collector {
        override fun collect(): List<Metric> {
            val labels = mapOf(
                "containerId" to MessagingModule.containerId.toString()
            )
            return emptyList()
//            return listOf(
//                GaugeMetric("Registered Instances", labels, MinecraftServer.getInstanceManager().instances.size),
//                GaugeMetric("Online Players", labels, MinecraftServer.getConnectionManager().onlinePlayers.size),
//                GaugeMetric("Entities", labels, MinecraftServer.getInstanceManager().instances.sumOf { it.entities.size }),
//            )
        }
    }
}
