package lucheart.zenithexporter.metrics;

import com.zenith.util.DisconnectMessages;

import java.util.List;
import java.util.Locale;

/**
 * Disconnect reasons are free text from the server, so they're reduced to a fixed set to use as a label.
 */
public final class DisconnectReasons {
    public static final String MANUAL = "manual";
    public static final String SYSTEM = "system";
    public static final String SERVER_RESTARTING = "server_restarting";
    public static final String SERVER_CLOSED = "server_closed";
    public static final String MAX_PLAYTIME = "max_playtime";
    public static final String LOGIN_FAILED = "login_failed";
    public static final String AUTH_REQUIRED = "auth_required";
    public static final String TIMEOUT = "timeout";
    public static final String OTHER = "other";
    public static final List<String> ALL = List.of(
        MANUAL, SYSTEM, SERVER_RESTARTING, SERVER_CLOSED, MAX_PLAYTIME, LOGIN_FAILED, AUTH_REQUIRED, TIMEOUT, OTHER);

    private DisconnectReasons() {}

    public static String classify(String reason) {
        if (reason == null) return OTHER;
        switch (reason) {
            case DisconnectMessages.MANUAL_DISCONNECT: return MANUAL;
            case DisconnectMessages.SYSTEM_DISCONNECT: return SYSTEM;
            case DisconnectMessages.SERVER_RESTARTING: return SERVER_RESTARTING;
            case DisconnectMessages.SERVER_CLOSING_MESSAGE: return SERVER_CLOSED;
            case DisconnectMessages.MAX_PT_DISCONNECT: return MAX_PLAYTIME;
            case DisconnectMessages.LOGIN_FAILED: return LOGIN_FAILED;
            case DisconnectMessages.AUTH_REQUIRED: return AUTH_REQUIRED;
            default: break;
        }
        var lower = reason.toLowerCase(Locale.ROOT);
        if (lower.contains("restart")) return SERVER_RESTARTING;
        if (lower.contains("timed out") || lower.contains("timeout")) return TIMEOUT;
        return OTHER;
    }
}
