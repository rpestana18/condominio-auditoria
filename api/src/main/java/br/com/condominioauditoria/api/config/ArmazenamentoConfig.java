package br.com.condominioauditoria.api.config;

import br.com.condominioauditoria.storage.Storage;
import br.com.condominioauditoria.storage.LocalStorage;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ArmazenamentoConfig {

    @Bean
    Storage armazenamento(PropriedadesCondominio propriedades) throws IOException {
        var config = propriedades.armazenamento();
        return switch (config.tipo()) {
            case "local" -> new LocalStorage(Path.of(config.pasta()));
            default -> throw new IllegalStateException("Tipo de armazenamento não suportado: " + config.tipo());
        };
    }
}
