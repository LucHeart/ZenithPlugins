package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;

import java.util.List;

public interface MetricGroup {
    /** Events this group counts, subscribed while the module is enabled. */
    default List<EventConsumer<?>> events() {
        return List.of();
    }
}
