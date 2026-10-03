package br.com.condominioauditoria.app.config;

import br.com.condominioauditoria.armazenamento.Armazenamento;
import br.com.condominioauditoria.armazenamento.ArmazenamentoLocal;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ArmazenamentoConfig {

    @Bean
    Armazenamento armazenamento(PropriedadesCondominio propriedades) throws IOException {
        var config = propriedades.armazenamento();
        return switch (config.tipo()) {
            case "local" -> new ArmazenamentoLocal(Path.of(config.pasta()));
            default -> throw new IllegalStateException("Tipo de armazenamento não suportado: " + config.tipo());
        };
    }
}
