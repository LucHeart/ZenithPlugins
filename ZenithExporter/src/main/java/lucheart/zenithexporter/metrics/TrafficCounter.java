package lucheart.zenithexporter.metrics;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.prometheus.metrics.core.metrics.Counter;

/** Sits at the front of the channel pipeline, so it sees bytes as they go over the wire. */
final class TrafficCounter extends ChannelDuplexHandler {
    static final String NAME = "zenith-exporter-traffic";

    private final Counter received;
    private final Counter sent;

    TrafficCounter(Counter received, Counter sent) {
        this.received = received;
        this.sent = sent;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) received.inc(buf.readableBytes());
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf) sent.inc(buf.readableBytes());
        super.write(ctx, msg, promise);
    }
}
