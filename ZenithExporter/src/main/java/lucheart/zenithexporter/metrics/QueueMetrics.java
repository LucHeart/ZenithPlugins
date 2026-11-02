package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.queue.QueueCompleteEvent;
import com.zenith.event.queue.QueueSkipEvent;
import com.zenith.event.queue.QueueStartEvent;
import com.zenith.feature.queue.Queue;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;

public final class QueueMetrics implements MetricGroup {
    private final Counter starts;
    private final Counter skips;
    private final Histogram wait;

    public QueueMetrics(Instruments instruments, ServerStatusPoller poller) {
        instruments.gauge("zenith_queue_position", "Position in the queue, while in it",
            cb -> { if (Proxy.getInstance().isInQueue()) cb.call(Proxy.getInstance().getQueuePosition()); });
        instruments.gauge("zenith_queue_eta_seconds", "Estimated time left in the queue, while in it",
            cb -> { if (Proxy.getInstance().isInQueue()) cb.call(Queue.getQueueWait(Proxy.getInstance().getQueuePosition())); });
        instruments.gauge("zenith_queue_priority", "Whether the account has queue priority",
            cb -> cb.call(Proxy.getInstance().isPrio() ? 1 : 0));
        instruments.gauge("zenith_2b2t_queue_length", "Players waiting in 2b2t's queue, known whether connected or not",
            cb -> {
                // our own ping is the fresher one, zenith's is there when that's off and only updates every few minutes
                var ping = poller.status();
                if (ping != null && ping.response().queue().isPresent() && ping.response().priorityQueue().isPresent()) {
                    cb.call(ping.response().queue().getAsInt(), "regular");
                    cb.call(ping.response().priorityQueue().getAsInt(), "priority");
                    return;
                }
                var status = poller.queueStatus();
                // 0 is the placeholder before the first update
                if (status == null || status.epochSecond() == 0) return;
                cb.call(status.regular(), "regular");
                cb.call(status.prio(), "priority");
            }, "queue");

        starts = instruments.counter("zenith_queue_starts_total", "Times the proxy entered the queue");
        skips = instruments.counter("zenith_queue_skips_total", "Times the queue was skipped");
        wait = instruments.histogram("zenith_queue_wait_seconds", "Time spent waiting in the queue",
            30, 60, 300, 900, 1800, 3600, 7200, 14400, 28800, 57600);
    }

    @Override
    public List<EventConsumer<?>> events() {
        return List.of(
            of(QueueStartEvent.class, e -> starts.inc()),
            of(QueueSkipEvent.class, e -> skips.inc()),
            of(QueueCompleteEvent.class, e -> wait.observe(e.queueDuration().toMillis() / 1000.0))
        );
    }
}
