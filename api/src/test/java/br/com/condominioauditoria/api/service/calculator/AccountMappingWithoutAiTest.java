package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * RF-03.1.5 and RF-09.7: account mapping works without AI (DESLIGADO mode) and no AI call happens. The api has no AI
 * client on the classpath, and no budget class talks to AI, to rag (the assistant's gRPC) or to the network: the
 * suggestion is the pure function {@link NameSuggestion}, which takes no collaborator.
 */
class AccountMappingWithoutAiTest {

    private static final List<String> AI_TERMS = List.of("org.springframework.ai", "anthropic", "openai", "ollama",
            "ChatClient", "ChatModel", "AssistenteGrpc", "RestClient", "WebClient", "HttpClient");

    /** Where the budget code lives, by layer (ADR 0006). */
    private static final List<String> BUDGET_DIRS = List.of("model/budget", "service/budget", "service/calculator",
            "controller/budget", "report");

    @Test
    void noAiClientInApi() {
        assertThatThrownBy(() -> Class.forName("org.springframework.ai.chat.client.ChatClient"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.springframework.ai.chat.model.ChatModel"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void noBudgetClassCallsAi() throws Exception {
        Path root = Path.of("src/main/java/br/com/condominioauditoria/api");
        for (String dir : BUDGET_DIRS) {
            Path pkg = root.resolve(dir);
            assertThat(pkg).isDirectory();
            try (Stream<Path> sources = Files.list(pkg)) {
                for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = Files.readString(source);
                    for (String term : AI_TERMS) {
                        assertThat(code).as(source.getFileName() + " não usa " + term).doesNotContain(term);
                    }
                }
            }
        }
    }

    @Test
    void suggestionIsPureFunctionWithoutCollaborator() {
        assertThat(NameSuggestion.class.getDeclaredConstructors()).singleElement()
                .satisfies(c -> assertThat(c.getParameterTypes()).containsExactly(AccountMappingProperties.class));
        assertThat(Stream.of(NameSuggestion.class.getDeclaredFields()).map(f -> f.getType().getName()))
                .allMatch(t -> t.startsWith("java."));
    }
}
