package br.com.condominioauditoria.rag.mensagens;

import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import java.util.List;
import java.util.UUID;

/** Mensagem rag → backend. Contrato: contracts/mensagens/v1/resultado-processamento.schema.json. */
public record ResultadoProcessamento(int versao, UUID processamentoId, UUID arquivoId, UUID condominioId,
        Situacao situacao, String motivo, String interpretador, Integer paginas, FluxoDeCaixa fluxoDeCaixa,
        List<Verificacao> conferencias) {

    public enum Situacao {
        INICIADO, CONCLUIDO, FALHOU
    }

    public static ResultadoProcessamento iniciado(ArquivoRecebido a) {
        return new ResultadoProcessamento(1, a.processamentoId(), a.arquivoId(), a.condominioId(), Situacao.INICIADO,
                null, null, null, null, null);
    }

    public static ResultadoProcessamento falhou(ArquivoRecebido a, String motivo) {
        return new ResultadoProcessamento(1, a.processamentoId(), a.arquivoId(), a.condominioId(), Situacao.FALHOU,
                motivo, null, null, null, null);
    }

    public static ResultadoProcessamento concluido(ArquivoRecebido a, String interpretador, int paginas,
            FluxoDeCaixa fluxo, List<Verificacao> conferencias) {
        return new ResultadoProcessamento(1, a.processamentoId(), a.arquivoId(), a.condominioId(), Situacao.CONCLUIDO,
                null, interpretador, paginas, fluxo, conferencias);
    }
}
