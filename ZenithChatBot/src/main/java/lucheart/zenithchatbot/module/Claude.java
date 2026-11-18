package lucheart.zenithchatbot.module;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static lucheart.zenithchatbot.ZenithChatBotPlugin.LOG;
import static lucheart.zenithchatbot.ZenithChatBotPlugin.PLUGIN_CONFIG;

/** The Claude API client, rebuilt when the API key changes. */
public final class Claude {
    // models that take fallbacks: "default", the API then picks another model by why the request was declined
    private static final Set<String> FALLBACK_MODELS = Set.of(
        "claude-fable-5-2", "claude-fable-5-1", "claude-mythos-5-2", "claude-mythos-5-1",
        "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    public static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");
    // a reply needs one tool round at most in practice, this only stops a model that keeps calling tools
    private static final int MAX_ROUNDS = 4;

    private AnthropicClient client;
    private String clientKey;

    /** A tool without input the model can call when it needs to, like the list of online players. */
    public record Tool(String name, String description, Supplier<String> run) {}

    /** A failed request, with a message fit to show the user. */
    public static final class ClaudeException extends Exception {
        public ClaudeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static boolean hasApiKey() {
        if (!PLUGIN_CONFIG.apiKey.isBlank()) return true;
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env != null && !env.isBlank();
    }

    /** The model's text answer, or null if it declined to answer. It may call the tools on the way. */
    public String ask(String system, String user, List<Tool> tools) throws ClaudeException {
        var params = MessageCreateParams.builder()
            .model(PLUGIN_CONFIG.model)
            // thinking counts toward this too, the reply itself is a single chat line
            .maxTokens(8000L)
            .system(system)
            .addUserMessage(user)
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(PLUGIN_CONFIG.effort)).build());
        if (PLUGIN_CONFIG.refusalFallback && FALLBACK_MODELS.contains(PLUGIN_CONFIG.model)) {
            params.addBeta(FALLBACK_BETA).fallbacksDefault();
        }
        for (Tool tool : tools) {
            params.addTool(BetaTool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(BetaTool.InputSchema.builder().properties(BetaTool.InputSchema.Properties.builder().build()).build())
                .build());
        }

        for (int round = 1; ; round++) {
            BetaMessage response = send(params);
            var stop = response.stopReason().orElse(null);
            if (BetaStopReason.REFUSAL.equals(stop)) {
                LOG.info("Claude declined to answer");
                return null;
            }
            if (!BetaStopReason.TOOL_USE.equals(stop) || round == MAX_ROUNDS) {
                return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .collect(Collectors.joining(" "));
            }
            // the response goes back unchanged (thinking blocks included), followed by every tool result in one message
            params.addMessage(response);
            List<BetaContentBlockParam> results = new ArrayList<>();
            response.content().stream().flatMap(block -> block.toolUse().stream()).forEach(call -> {
                var tool = tools.stream().filter(t -> t.name().equals(call.name())).findFirst();
                var result = BetaToolResultBlockParam.builder().toolUseId(call.id());
                if (tool.isEmpty()) {
                    result.content("Unknown tool " + call.name()).isError(true);
                } else {
                    LOG.info("Claude used {}", call.name());
                    result.content(tool.get().run().get());
                }
                results.add(BetaContentBlockParam.ofToolResult(result.build()));
            });
            params.addUserMessageOfContentBlockParams(results);
        }
    }

    private BetaMessage send(MessageCreateParams.Builder params) throws ClaudeException {
        try {
            return client().beta().messages().create(params.build());
        } catch (AnthropicServiceException e) {
            throw new ClaudeException("Claude API error " + e.statusCode() + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ClaudeException("Claude API request failed: " + e.getMessage(), e);
        }
    }

    private synchronized AnthropicClient client() throws ClaudeException {
        String key = PLUGIN_CONFIG.apiKey.strip();
        if (client != null && key.equals(clientKey)) return client;
        close();
        if (!hasApiKey()) throw new ClaudeException("No API key, set one with `chatBot apiKey <key>`", null);
        var builder = AnthropicOkHttpClient.builder();
        if (key.isEmpty()) builder.fromEnv();
        else builder.apiKey(key);
        client = builder.timeout(Duration.ofSeconds(60)).maxRetries(1).build();
        clientKey = key;
        return client;
    }

    public synchronized void close() {
        if (client != null) client.close();
        client = null;
        clientKey = null;
    }
}
