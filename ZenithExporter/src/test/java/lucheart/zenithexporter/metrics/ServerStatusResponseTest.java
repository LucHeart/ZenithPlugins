package lucheart.zenithexporter.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerStatusResponseTest {
    @Test
    void parsesFullResponse() {
        var status = ServerStatusResponse.parse("""
            {"version":{"name":"1.21.4","protocol":769},"players":{"max":1000,"online":777,"sample":[]},
             "description":{"text":"hi"},"favicon":"data:image/png;base64,AA"}""");
        assertEquals(777, status.online().getAsInt());
        assertEquals(1000, status.max().getAsInt());
        assertEquals(769, status.protocol().getAsInt());
    }

    @Test
    void parses2b2tResponse() {
        // shape of a real response from 2b2t.org
        var status = ServerStatusResponse.parse("""
            {"version":{"name":"Velocity 1.7.2-26.3","protocol":-1},
             "players":{"max":1,"online":1314,"sample":[
               {"name":"§6In-game: 566","id":"00000000-0000-0000-0000-000000000000"},
               {"name":"§6Queue: 749","id":"00000000-0000-0000-0000-000000000000"},
               {"name":"§6Priority queue: 1","id":"00000000-0000-0000-0000-000000000000"}]},
             "description":{"text":"2b2t"},"favicon":"data:image/png;base64,AA"}""");
        assertEquals(566, status.online().getAsInt());
        assertEquals(1314, status.reported().getAsInt());
        assertEquals(749, status.queue().getAsInt());
        assertEquals(1, status.priorityQueue().getAsInt());
        assertTrue(status.max().isEmpty(), "placeholder limit");
        assertTrue(status.protocol().isEmpty(), "placeholder protocol");
    }

    @Test
    void realPlayerNamesInTheSampleAreNotStats() {
        var status = ServerStatusResponse.parse("""
            {"version":{"protocol":769},"players":{"max":100,"online":2,"sample":[{"name":"Queue","id":"a"},{"name":"Steve","id":"b"}]}}""");
        assertEquals(2, status.online().getAsInt());
        assertTrue(status.queue().isEmpty());
    }

    @Test
    void acceptsWhatStopsZenithsParser() {
        var status = ServerStatusResponse.parse("""
            {"version":{"name":"1.21.4","protocol":769},"players":{"max":20,"online":3},"description":"plain text"}""");
        assertEquals(3, status.online().getAsInt());
    }

    @Test
    void hiddenPlayerCountIsMissingNotZero() {
        var status = ServerStatusResponse.parse("""
            {"version":{"name":"Paper","protocol":769},"description":"hidden"}""");
        assertTrue(status.online().isEmpty());
        assertTrue(status.max().isEmpty());
        assertEquals(769, status.protocol().getAsInt());
    }

    @Test
    void garbageGivesNothing() {
        for (var json : new String[]{null, "", "not json", "[]", "{\"players\":\"many\"}"}) {
            var status = ServerStatusResponse.parse(json);
            assertTrue(status.online().isEmpty(), json);
            assertTrue(status.protocol().isEmpty(), json);
        }
    }
}
