package lucheart.zenithchat.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lucheart.zenithchat.module.ChatOutbox;
import lucheart.zenithchat.module.ChatOutbox.Result;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static com.zenith.Globals.OBJECT_MAPPER;
import static lucheart.zenithchat.ZenithChatPlugin.LOG;
import static lucheart.zenithchat.ZenithChatPlugin.PLUGIN_CONFIG;

/**
 * REST endpoint for other programs, every request needs "Authorization: Bearer <apiToken>".
 * <ul>
 *   <li>POST /chat {"message": "..."}: public chat</li>
 *   <li>POST /dm {"player": "...", "message": "..."}: DM a player</li>
 *   <li>GET /status: whether messages can be sent right now</li>
 * </ul>
 * Messages are queued, 202 means accepted, not yet sent.
 */
public final class ChatApiServer {
    // a chat message plus JSON around it, anything bigger isn't a valid request
    private static final int MAX_BODY = 4096;

    private final ChatOutbox outbox;
    private HttpServer server;

    public ChatApiServer(ChatOutbox outbox) {
        this.outbox = outbox;
    }

    public record ChatRequest(String message) {}

    public record DmRequest(String player, String message) {}

    public synchronized boolean start() {
        if (server != null) return true;
        try {
            server = HttpServer.create(new InetSocketAddress(PLUGIN_CONFIG.apiBindAddress, PLUGIN_CONFIG.apiPort), 0);
        } catch (IOException | RuntimeException e) {
            server = null;
            LOG.error("Failed starting chat API on {}:{}", PLUGIN_CONFIG.apiBindAddress, PLUGIN_CONFIG.apiPort, e);
            return false;
        }
        server.createContext("/chat", exchange -> handle(exchange, "POST", () -> {
            var request = OBJECT_MAPPER.readValue(readBody(exchange), ChatRequest.class);
            if (request == null || request.message() == null) return badRequest("message is required");
            return queued(outbox.say(request.message(), PLUGIN_CONFIG.apiAllowCommands));
        }));
        server.createContext("/dm", exchange -> handle(exchange, "POST", () -> {
            var request = OBJECT_MAPPER.readValue(readBody(exchange), DmRequest.class);
            if (request == null || request.player() == null || request.message() == null) return badRequest("player and message are required");
            return queued(outbox.dm(request.player(), request.message()));
        }));
        server.createContext("/status", exchange -> handle(exchange, "GET", () ->
            new Response(200, Map.of("online", ChatOutbox.canChat(), "queued", outbox.queued()))));
        server.start();
        LOG.info("Serving chat API on {}", endpoint());
        if (!PLUGIN_CONFIG.apiBindAddress.equals("127.0.0.1") && !PLUGIN_CONFIG.apiBindAddress.equals("localhost")) {
            LOG.warn("Chat API is reachable from other hosts, anyone with the token can chat as the account");
        }
        return true;
    }

    public synchronized void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public String endpoint() {
        return "http://" + PLUGIN_CONFIG.apiBindAddress + ":" + PLUGIN_CONFIG.apiPort;
    }

    private record Response(int status, Map<String, Object> body) {}

    @FunctionalInterface
    private interface Handler {
        Response handle() throws Exception;
    }

    private void handle(HttpExchange exchange, String method, Handler handler) throws IOException {
        Response response;
        try {
            if (!authorized(exchange)) {
                response = error(401, "missing or wrong bearer token");
            } else if (!method.equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", method);
                response = error(405, "use " + method);
            } else {
                response = handler.handle();
            }
        } catch (BodyTooLargeException e) {
            response = error(413, "body over " + MAX_BODY + " bytes");
        } catch (Exception e) {
            // mostly malformed JSON
            LOG.debug("Bad chat API request", e);
            response = badRequest("invalid JSON body");
        }
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(response.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static boolean authorized(HttpExchange exchange) {
        String token = PLUGIN_CONFIG.apiToken;
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (token.isEmpty() || header == null || !header.startsWith("Bearer ")) return false;
        // constant time, how much of a guess matched shouldn't show in the response time
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), header.substring(7).strip().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) throw new BodyTooLargeException();
            return body;
        }
    }

    private static Response queued(Result result) {
        return switch (result) {
            case QUEUED -> new Response(202, Map.of("queued", true));
            case OFFLINE -> error(503, "not in game");
            case QUEUE_FULL -> error(429, "too many messages waiting to be sent");
            case TOO_LONG -> badRequest("message too long, the limit is " + ChatOutbox.MAX_LENGTH + " characters (for DMs including the whisper command)");
            case INVALID -> badRequest("empty message or invalid player name");
            case COMMAND_NOT_ALLOWED -> error(403, "messages starting with / are commands, enable apiAllowCommands to allow them");
        };
    }

    private static Response badRequest(String message) {
        return error(400, message);
    }

    private static Response error(int status, String message) {
        return new Response(status, Map.of("error", message));
    }

    private static final class BodyTooLargeException extends IOException {}
}
