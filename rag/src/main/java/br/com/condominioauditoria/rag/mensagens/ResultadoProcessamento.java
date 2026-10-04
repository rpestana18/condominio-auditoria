package br.com.condominioauditoria.rag.mensagens;

import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria;
import java.util.List;
import java.util.UUID;

/** Mensagem rag → backend. Contrato: contracts/mensagens/v2/resultado-processamento.schema.json. */
public record ResultadoProcessamento(int versao, UUID processamentoId, UUID arquivoId, UUID condominioId,
        Situacao situacao, String motivo, String interpretador, Integer paginas, FluxoDeCaixa fluxoDeCaixa,
        PrevisaoOrcamentaria previsaoOrcamentaria, List<Verificacao> conferencias) {

    /** Versão do contrato publicada pelo rag. A v1 não é mais aceita pelo backend (ADR 0004, Decisão 2). */
    public static final int VERSAO = 2;

    public enum Situacao {
        INICIADO, CONCLUIDO, FALHOU
    }

    public static ResultadoProcessamento iniciado(ArquivoRecebido a) {
        return new ResultadoProcessamento(VERSAO, a.processamentoId(), a.arquivoId(), a.condominioId(),
                Situacao.INICIADO, null, null, null, null, null, null);
    }

    public static ResultadoProcessamento falhou(ArquivoRecebido a, String motivo) {
        return new ResultadoProcessamento(VERSAO, a.processamentoId(), a.arquivoId(), a.condominioId(),
                Situacao.FALHOU, motivo, null, null, null, null, null);
    }

    /** Fluxo de caixa lido, ou layout ainda sem leitor (fluxo nulo). */
    public static ResultadoProcessamento concluido(ArquivoRecebido a, String interpretador, int paginas,
            FluxoDeCaixa fluxo, List<Verificacao> conferencias) {
        return new ResultadoProcessamento(VERSAO, a.processamentoId(), a.arquivoId(), a.condominioId(),
                Situacao.CONCLUIDO, null, interpretador, paginas, fluxo, null, conferencias);
    }

    /** PO lida. */
    public static ResultadoProcessamento concluidoPo(ArquivoRecebido a, String interpretador, int paginas,
            PrevisaoOrcamentaria previsao, List<Verificacao> conferencias) {
        return new ResultadoProcessamento(VERSAO, a.processamentoId(), a.arquivoId(), a.condominioId(),
                Situacao.CONCLUIDO, null, interpretador, paginas, null, previsao, conferencias);
    }
}
