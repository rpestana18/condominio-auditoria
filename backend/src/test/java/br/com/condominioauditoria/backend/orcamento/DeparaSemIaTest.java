package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * RF-03.1.5 e RF-09.7: o de-para funciona sem IA (modo DESLIGADO) e nenhuma chamada de IA acontece. O backend não tem
 * cliente de IA no classpath, e nenhuma classe do orçamento fala com IA, com o rag (gRPC do assistente) ou com a rede:
 * a sugestão é a função pura {@link SugestaoPorNome}, que não recebe colaborador nenhum.
 */
class DeparaSemIaTest {

    private static final List<String> TERMOS_DE_IA = List.of("org.springframework.ai", "anthropic", "openai", "ollama",
            "ChatClient", "ChatModel", "AssistenteGrpc", "RestClient", "WebClient", "HttpClient");

    @Test
    void semClienteDeIaNoBackend() {
        assertThatThrownBy(() -> Class.forName("org.springframework.ai.chat.client.ChatClient"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.springframework.ai.chat.model.ChatModel"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void nenhumaClasseDoOrcamentoChamaIa() throws Exception {
        Path pacote = Path.of("src/main/java/br/com/condominioauditoria/backend/orcamento");
        assertThat(pacote).isDirectory();
        try (Stream<Path> fontes = Files.list(pacote)) {
            for (Path fonte : fontes.filter(p -> p.toString().endsWith(".java")).toList()) {
                String codigo = Files.readString(fonte);
                for (String termo : TERMOS_DE_IA) {
                    assertThat(codigo).as(fonte.getFileName() + " não usa " + termo).doesNotContain(termo);
                }
            }
        }
    }

    @Test
    void sugestaoEhFuncaoPuraSemColaborador() {
        assertThat(SugestaoPorNome.class.getDeclaredConstructors()).singleElement()
                .satisfies(c -> assertThat(c.getParameterTypes()).containsExactly(PropriedadesDepara.class));
        assertThat(Stream.of(SugestaoPorNome.class.getDeclaredFields()).map(f -> f.getType().getName()))
                .allMatch(t -> t.startsWith("java."));
    }
}
