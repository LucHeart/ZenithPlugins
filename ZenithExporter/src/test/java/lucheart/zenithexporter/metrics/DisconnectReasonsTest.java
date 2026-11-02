package lucheart.zenithexporter.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisconnectReasonsTest {
    @Test
    void knownMessages() {
        assertEquals(DisconnectReasons.MANUAL, DisconnectReasons.classify("Manual Disconnect"));
        assertEquals(DisconnectReasons.SERVER_RESTARTING, DisconnectReasons.classify("Server restarting"));
        assertEquals(DisconnectReasons.LOGIN_FAILED, DisconnectReasons.classify("Login Failed"));
    }

    @Test
    void freeTextEndsUpInAFixedSet() {
        assertEquals(DisconnectReasons.TIMEOUT, DisconnectReasons.classify("Connection timed out"));
        assertEquals(DisconnectReasons.SERVER_RESTARTING, DisconnectReasons.classify("The server is restarting in 5 minutes"));
        assertEquals(DisconnectReasons.OTHER, DisconnectReasons.classify("You were kicked by an admin: spam"));
        assertEquals(DisconnectReasons.OTHER, DisconnectReasons.classify(null));
        assertTrue(DisconnectReasons.ALL.contains(DisconnectReasons.classify("anything else")));
    }
}
