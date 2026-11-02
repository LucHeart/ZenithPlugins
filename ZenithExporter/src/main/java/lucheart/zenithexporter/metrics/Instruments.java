package lucheart.zenithexporter.metrics;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Registers metrics while keeping failures contained. An exception from a gauge callback would otherwise
 * fail the whole scrape, and packet handlers must never break the packet flow because of a metric.
 */
public final class Instruments {
    private final PrometheusRegistry registry;
    private final BiConsumer<String, Throwable> errorLog;
    private final Counter collectorErrors;
    // zenith state is read from other threads, so a failure can repeat on every scrape: log each one once
    private final Set<String> logged = ConcurrentHashMap.newKeySet();

    public Instruments(PrometheusRegistry registry, BiConsumer<String, Throwable> errorLog) {
        this.registry = registry;
        this.errorLog = errorLog;
        this.collectorErrors = counter("zenith_exporter_collector_errors_total", "Failures while collecting a metric", "collector");
    }

    /** A gauge read on every scrape. A callback that doesn't call back leaves the series out of that scrape. */
    public void gauge(String name, String help, Consumer<GaugeWithCallback.Callback> collect, String... labels) {
        var builder = GaugeWithCallback.builder()
            .name(name)
            .help(help)
            .callback(callback -> guard(name, () -> collect.accept(callback)));
        if (labels.length > 0) builder.labelNames(labels);
        builder.register(registry);
    }

    public Counter counter(String name, String help, String... labels) {
        var builder = Counter.builder().name(name).help(help);
        if (labels.length > 0) builder.labelNames(labels);
        return builder.register(registry);
    }

    public Histogram histogram(String name, String help, double... upperBounds) {
        return Histogram.builder()
            .name(name)
            .help(help)
            .classicOnly()
            .classicUpperBounds(upperBounds)
            .register(registry);
    }

    public Info info(String name, String help, String... labels) {
        return Info.builder().name(name).help(help).labelNames(labels).register(registry);
    }

    public void guard(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            collectorErrors.labelValues(name).inc();
            if (logged.add(name)) errorLog.accept(name, e);
        }
    }
}
