package br.com.condominioauditoria.rag.config;

import br.com.condominioauditoria.rag.parser.ReaderContract;
import br.com.condominioauditoria.rag.parser.budget.ProtestBudgetParser;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import br.com.condominioauditoria.storage.LocalStorage;
import br.com.condominioauditoria.storage.Storage;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public Storage armazenamento(PropriedadesRag propriedades) throws IOException {
        var config = propriedades.armazenamento();
        return switch (config.tipo()) {
            case "local" -> new LocalStorage(Path.of(config.pasta()));
            default -> throw new IllegalStateException("Tipo de armazenamento não suportado: " + config.tipo());
        };
    }

    @Bean
    public ReaderContract contratoLeitor() {
        return new ReaderContract();
    }

    @Bean
    public CashFlowParser interpretadorFluxoCaixa() {
        return new CashFlowParser();
    }

    @Bean
    public ProtestBudgetParser interpretadorPoProtest() {
        return new ProtestBudgetParser();
    }
}
