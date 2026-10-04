package br.com.condominioauditoria.rag.config;

import br.com.condominioauditoria.armazenamento.Armazenamento;
import br.com.condominioauditoria.armazenamento.ArmazenamentoLocal;
import br.com.condominioauditoria.rag.leitura.contrato.ContratoLeitor;
import br.com.condominioauditoria.rag.leitura.fluxo.InterpretadorFluxoCaixa;
import br.com.condominioauditoria.rag.leitura.po.InterpretadorPoProtest;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RagConfig {

    @Bean
    Armazenamento armazenamento(PropriedadesRag propriedades) throws IOException {
        var config = propriedades.armazenamento();
        return switch (config.tipo()) {
            case "local" -> new ArmazenamentoLocal(Path.of(config.pasta()));
            default -> throw new IllegalStateException("Tipo de armazenamento não suportado: " + config.tipo());
        };
    }

    @Bean
    ContratoLeitor contratoLeitor() {
        return new ContratoLeitor();
    }

    @Bean
    InterpretadorFluxoCaixa interpretadorFluxoCaixa() {
        return new InterpretadorFluxoCaixa();
    }

    @Bean
    InterpretadorPoProtest interpretadorPoProtest() {
        return new InterpretadorPoProtest();
    }
}
