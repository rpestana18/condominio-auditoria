package br.com.condominioauditoria.app.processamento;

import br.com.condominioauditoria.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.dominio.fluxo.Verificacao;
import java.util.List;

/** O que a leitura produziu, ainda em memória, antes de ir para o banco numa transação só. */
sealed interface ResultadoLeitura {

    /** Fluxo de caixa reconhecido e conferido. */
    record Fluxo(FluxoDeCaixa fluxo, List<Verificacao> conferencias) implements ResultadoLeitura {
    }

    /** Arquivo lido, mas ainda não há leitor específico para o layout (fica disponível para o RAG mais tarde). */
    record SemInterpretador(int paginas) implements ResultadoLeitura {
    }
}
