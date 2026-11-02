package lucheart.zenithexporter.module;

import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandlerStateCodec;
import lucheart.zenithexporter.metrics.WorldMetrics;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;

final class EntitySpawnPackets {
    private EntitySpawnPackets() {}

    static PacketHandlerStateCodec.Builder<ClientSession> handle(PacketHandlerStateCodec.Builder<ClientSession> codec, WorldMetrics world) {
        return codec.inbound(ClientboundAddEntityPacket.class, (packet, session) -> {
            world.onEntitySpawn(packet.getEntityId(), packet.getType());
            return packet;
        });
    }
}
