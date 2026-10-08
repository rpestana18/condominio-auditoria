package br.com.condominioauditoria.api.mensagens;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.processamento.GravacaoResultado;
import br.com.condominioauditoria.api.processamento.StatusArquivoService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * ADR 0004, Decisão 2 (corte da v1): resultado v1 é recusado sem tocar no banco. A exceção faz o contêiner da fila
 * rejeitar a mensagem sem devolvê-la (default-requeue-rejected: false), e o RabbitMQ a envia para
 * backend.resultados.erro (dead-letter declarada em Filas).
 */
class ReceptorResultadosTest {

    private final StatusArquivoService status = mock(StatusArquivoService.class);
    private final GravacaoResultado gravacao = mock(GravacaoResultado.class);
    private final ReceptorResultados receptor = new ReceptorResultados(new ContratoMensagens(), status, gravacao);

    @Test
    void resultadoV1EhRecusadoENaoGravaNada() throws Exception {
        var mensagem = new Message(ContratoMensagensTest.exemplo("v1", "resultado-concluido.json"),
                new MessageProperties());

        assertThatThrownBy(() -> receptor.aoReceber(mensagem)).hasMessageContaining("fora do contrato");
        verifyNoInteractions(status, gravacao);
    }

    @Test
    void resultadoV2DeFluxoVaiParaAGravacao() throws Exception {
        var mensagem = new Message(ContratoMensagensTest.exemplo("v2", "resultado-concluido-fluxo.json"),
                new MessageProperties());

        receptor.aoReceber(mensagem);

        verify(gravacao).gravar(org.mockito.ArgumentMatchers.argThat(r -> r.versao() == 2));
    }

    @Test
    void resultadoV2DaPoEhAceito() throws Exception {
        var mensagem = new Message(ContratoMensagensTest.exemplo("v2", "resultado-concluido-po.json"),
                new MessageProperties());

        receptor.aoReceber(mensagem);

        verify(gravacao).gravar(org.mockito.ArgumentMatchers.argThat(r -> r.previsaoOrcamentaria() != null));
    }
}
