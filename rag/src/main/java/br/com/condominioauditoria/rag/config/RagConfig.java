package br.com.condominioauditoria.rag.config;

import br.com.condominioauditoria.rag.config.properties.RagProperties;
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
    public Storage storage(RagProperties properties) throws IOException {
        var config = properties.storage();
        return switch (config.type()) {
            case "local" -> new LocalStorage(Path.of(config.folder()));
            default -> throw new IllegalStateException("Tipo de armazenamento não suportado: " + config.type());
        };
    }

    @Bean
    public ReaderContract readerContract() {
        return new ReaderContract();
    }

    @Bean
    public CashFlowParser cashFlowParser() {
        return new CashFlowParser();
    }

    @Bean
    public ProtestBudgetParser protestBudgetParser() {
        return new ProtestBudgetParser();
    }
}
