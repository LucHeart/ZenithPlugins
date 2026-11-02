package lucheart.zenithanoying;

public class AnoyingConfig {
    public final BlockSpamConfig blockSpam = new BlockSpamConfig();
    public static class BlockSpamConfig {
        public boolean enabled = false;
        public String block = "bell";
        public int intervalTicks = 1;
        public int searchRadiusChunks = 16;
        public boolean pathing = true;
    }
}
