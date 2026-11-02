package lucheart.zenithexporter.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabFooterTest {
    @Test
    void parses2b2tFooter() {
        var footer = TabFooter.parse("19.51 tps — 679 players online — 127 ping");
        assertEquals(679, footer.players().getAsInt());
        assertEquals(19.51, footer.tps().getAsDouble());
        assertEquals(127, footer.pingMs().getAsInt());
    }

    @Test
    void ignoresFormattingCodesAndCase() {
        var footer = TabFooter.parse("§a20 TPS §7- §b1,234 Players Online");
        assertEquals(1234, footer.players().getAsInt());
        assertEquals(20.0, footer.tps().getAsDouble());
        assertTrue(footer.pingMs().isEmpty());
    }

    @Test
    void parsesLabelFirstStyle() {
        var footer = TabFooter.parse("Online: 42\nTPS: 19.9\nPing: 35");
        assertEquals(42, footer.players().getAsInt());
        assertEquals(19.9, footer.tps().getAsDouble());
        assertEquals(35, footer.pingMs().getAsInt());
    }

    @Test
    void decimalCommaFromAServerLocale() {
        var footer = TabFooter.parse("19,55 tps — 1,234 players online — 127 ping");
        assertEquals(19.55, footer.tps().getAsDouble());
        assertEquals(1234, footer.players().getAsInt());
    }

    @Test
    void singlePlayerIsNotPlural() {
        assertEquals(1, TabFooter.parse("1 player online").players().getAsInt());
    }

    @Test
    void footerWithoutStatsGivesNothing() {
        for (var text : new String[]{"", "welcome to the server", "store.example.com", "online since 2011"}) {
            var footer = TabFooter.parse(text);
            assertTrue(footer.players().isEmpty(), text);
            assertTrue(footer.tps().isEmpty(), text);
            assertTrue(footer.pingMs().isEmpty(), text);
        }
    }

    @Test
    void absurdNumberDoesNotThrow() {
        assertTrue(TabFooter.parse("99999999999999 players online").players().isEmpty());
    }
}
