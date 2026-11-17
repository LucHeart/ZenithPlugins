package lucheart.zenithchat.module;

import com.zenith.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;

import java.util.Comparator;
import java.util.List;

import static com.zenith.Globals.CACHE;

/**
 * The other players the account has loaded around it. The server sends invisible players too, only flagged as such,
 * so this sees players that a web map like BlueMap hides.
 */
public final class NearbyPlayers {
    // entity flags in metadata 0, see https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Entity
    private static final int INVISIBLE_FLAG = 0x20;

    private NearbyPlayers() {}

    public record Nearby(String uuid, String name, double x, double y, double z, boolean invisible) {}

    /** Out of game the dimension is empty and there are no players. */
    public record Snapshot(boolean inGame, String dimension, List<Nearby> players) {
        static final Snapshot OUT_OF_GAME = new Snapshot(false, "", List.of());
    }

    public static Snapshot snapshot() {
        if (!ChatOutbox.canChat()) return Snapshot.OUT_OF_GAME;

        var dimension = CACHE.getChunkCache().getCurrentDimension();
        int self = CACHE.getPlayerCache().getEntityId();
        var players = CACHE.getEntityCache().getPlayers().values().stream()
            .filter(player -> player.getEntityId() != self && player.getUuid() != null && !player.isRemoved())
            .map(NearbyPlayers::describe)
            .sorted(Comparator.comparing(Nearby::uuid))
            .toList();
        return new Snapshot(true, dimension == null ? "" : dimension.name(), players);
    }

    private static Nearby describe(EntityPlayer player) {
        // the tab list has the name, the entity only the UUID
        String name = CACHE.getTabListCache().get(player.getUuid()).map(PlayerListEntry::getName).orElse("");
        Byte flags = player.getMetadataValue(0, MetadataTypes.BYTE, Byte.class);
        boolean invisible = flags != null && (flags & INVISIBLE_FLAG) != 0;
        return new Nearby(player.getUuid().toString(), name, player.getX(), player.getY(), player.getZ(), invisible);
    }
}
