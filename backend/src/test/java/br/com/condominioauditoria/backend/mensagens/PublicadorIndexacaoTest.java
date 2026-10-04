package br.com.condominioauditoria.backend.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.arquivo.SituacaoIndexacao;
import br.com.condominioauditoria.backend.config.PropriedadesCondominio;
import br.com.condominioauditoria.backend.mensagens.PublicadorIndexacao.ArquivoParaIndexar;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Pedido de indexação vai para rag.indexacao no contrato; a varredura reenvia o que parou e desiste depois de 3. */
class PublicadorIndexacaoTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final PublicadorIndexacao publicador = new PublicadorIndexacao(rabbit, new ContratoMensagens(), arquivos,
            mock(PlatformTransactionManager.class),
            new PropriedadesCondominio(null, new PropriedadesCondominio.Processamento(15, 3), null, null));

    @Test
    void aposOCommitPublicaNaFilaDeIndexacao() {
        Arquivo arquivo = arquivo();
        when(arquivos.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));

        publicador.aposCommit(new ArquivoParaIndexar(arquivo.getId()));

        var mensagem = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(""), eq(Filas.INDEXACAO), mensagem.capture());
        String json = new String(mensagem.getValue().getBody(), StandardCharsets.UTF_8);
        assertThat(json).contains(arquivo.getIndexacaoId().toString()).contains("\"operacao\":\"INDEXAR\"");
    }

    @Test
    void varreduraReenviaOMesmoPedidoEDesisteNaTerceiraTentativa() {
        Arquivo parado = arquivo();
        UUID pedido = parado.getIndexacaoId();
        Arquivo esgotado = arquivo();
        esgotado.reenviarIndexacao();
        esgotado.reenviarIndexacao(); // 3 tentativas
        when(arquivos.findByIndexacaoSituacaoInAndIndexacaoEnfileiradaEmBeforeOrderByEnviadoEm(anyCollection(), any()))
                .thenReturn(List.of(parado, esgotado));

        publicador.varrer(Instant.now());

        assertThat(parado.getIndexacaoTentativas()).isEqualTo(2);
        assertThat(parado.getIndexacaoId()).isEqualTo(pedido);
        assertThat(parado.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
        assertThat(esgotado.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.ERRO);
        assertThat(esgotado.getIndexacaoMotivo()).contains("3 tentativas");
        verify(rabbit, times(1)).send(eq(""), eq(Filas.INDEXACAO), any(Message.class));
    }

    @Test
    void varreduraSemParadosNaoPublica() {
        when(arquivos.findByIndexacaoSituacaoInAndIndexacaoEnfileiradaEmBeforeOrderByEnviadoEm(anyCollection(), any()))
                .thenReturn(List.of());

        publicador.varrer(Instant.now());

        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    private static Arquivo arquivo() {
        return new Arquivo(UUID.randomUUID(), Categoria.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "b".repeat(64), 10, "application/pdf", "gestor");
    }
}
