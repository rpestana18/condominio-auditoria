package br.com.condominioauditoria.api.config;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.storage.LocalStorage;
import br.com.condominioauditoria.storage.Storage;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StorageConfig {

    @Bean
    Storage storage(ApiProperties properties) throws IOException {
        var config = properties.storage();
        return switch (config.type()) {
            case "local" -> new LocalStorage(Path.of(config.folder()));
            default -> throw new IllegalStateException("Tipo de armazenamento não suportado: " + config.type());
        };
    }
}
