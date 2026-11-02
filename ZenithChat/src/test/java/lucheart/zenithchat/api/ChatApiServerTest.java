package lucheart.zenithchat.api;

import lucheart.zenithchat.ZenithChatConfig;
import lucheart.zenithchat.ZenithChatPlugin;
import lucheart.zenithchat.module.ChatOutbox;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// only requests that are answered before anything checks whether the account is in game
class ChatApiServerTest {
    private static final String TOKEN = "secret-token";
    private static ChatApiServer server;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        ZenithChatPlugin.LOG = ComponentLogger.logger("test");
        ZenithChatPlugin.PLUGIN_CONFIG = new ZenithChatConfig();
        try (var socket = new ServerSocket(0)) {
            ZenithChatPlugin.PLUGIN_CONFIG.apiPort = socket.getLocalPort();
        }
        ZenithChatPlugin.PLUGIN_CONFIG.apiToken = TOKEN;
        server = new ChatApiServer(new ChatOutbox());
        assertTrue(server.start());
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    private static HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(server.endpoint() + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        assertEquals(401, send("POST", "/chat", null, "{\"message\":\"hi\"}").statusCode());
        assertEquals(401, send("POST", "/chat", "wrong", "{\"message\":\"hi\"}").statusCode());
    }

    @Test
    void rejectsWrongMethod() throws Exception {
        var response = send("GET", "/chat", TOKEN, null);
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void rejectsBadBodies() throws Exception {
        assertEquals(400, send("POST", "/chat", TOKEN, "not json").statusCode());
        assertEquals(400, send("POST", "/chat", TOKEN, "").statusCode());
        assertEquals(400, send("POST", "/chat", TOKEN, "{}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"message\":\"hi\"}").statusCode());
        assertEquals(413, send("POST", "/chat", TOKEN, "{\"message\":\"" + "a".repeat(5000) + "\"}").statusCode());
    }

    @Test
    void rejectsBadMessages() throws Exception {
        var command = send("POST", "/chat", TOKEN, "{\"message\":\"/kill\"}");
        assertEquals(403, command.statusCode());
        assertTrue(command.body().contains("apiAllowCommands"), command.body());
        assertEquals(400, send("POST", "/chat", TOKEN, "{\"message\":\"" + "a".repeat(300) + "\"}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"player\":\"not a name\",\"message\":\"hi\"}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"player\":\"Steve\",\"message\":\"  \"}").statusCode());
    }

    @Test
    void rejectsBadBulkDms() throws Exception {
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"player\":\"Steve\",\"players\":[\"Alex\"],\"message\":\"hi\"}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"players\":[],\"message\":\"hi\"}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"players\":[\"Steve\",\"not a name\"],\"message\":\"hi\"}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"players\":[\"Steve\",null],\"message\":\"hi\"}").statusCode());
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"players\":[\"Steve\"],\"message\":\" \"}").statusCode());
        var tooMany = "\"a\",".repeat(101);
        assertEquals(400, send("POST", "/dm", TOKEN, "{\"players\":[" + tooMany.substring(0, tooMany.length() - 1) + "],\"message\":\"hi\"}").statusCode());
    }
}
