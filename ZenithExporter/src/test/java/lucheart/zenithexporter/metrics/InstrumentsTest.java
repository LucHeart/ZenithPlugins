package lucheart.zenithexporter.metrics;

import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentsTest {
    private final PrometheusRegistry registry = new PrometheusRegistry();
    private final List<String> logged = new ArrayList<>();
    private final Instruments instruments = new Instruments(registry, (name, e) -> logged.add(name));

    private String scrape() throws IOException {
        var out = new ByteArrayOutputStream();
        PrometheusTextFormatWriter.create().write(out, registry.scrape());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void failingGaugeDoesNotFailTheScrape() throws IOException {
        instruments.gauge("broken", "throws", cb -> { throw new IllegalStateException("nope"); });
        instruments.gauge("working", "fine", cb -> cb.call(7));

        var first = scrape();
        assertTrue(first.contains("working 7.0"), first);

        // the registry has no fixed collection order, so the first failure may only show from the next scrape on
        var second = scrape();
        assertTrue(second.contains("working 7.0"), second);
        assertTrue(second.contains("zenith_exporter_collector_errors_total{collector=\"broken\"}"), second);
        assertEquals(List.of("broken"), logged);
    }

    @Test
    void gaugeWithoutValueLeavesTheSeriesOut() throws IOException {
        instruments.gauge("sometimes", "only when known", cb -> {});
        assertFalse(scrape().contains("sometimes "));
    }

    @Test
    void labelledGauge() throws IOException {
        instruments.gauge("players", "by source", cb -> {
            cb.call(10, "tablist");
            cb.call(12, "footer");
        }, "source");
        var text = scrape();
        assertTrue(text.contains("players{source=\"tablist\"} 10.0"), text);
        assertTrue(text.contains("players{source=\"footer\"} 12.0"), text);
    }

    @Test
    void guardSwallowsAndCounts() throws IOException {
        instruments.guard("handler", () -> { throw new RuntimeException("nope"); });
        assertTrue(scrape().contains("collector=\"handler\"} 1.0"));
    }

    @Test
    void infoMetricGetsItsSuffix() throws IOException {
        instruments.info("zenith_build", "versions", "version").setLabelValues("1.0.0");
        assertTrue(scrape().contains("zenith_build_info{version=\"1.0.0\"} 1"));
    }
}
