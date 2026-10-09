package br.com.condominioauditoria.api.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.arquivo.ArquivoRepository;
import br.com.condominioauditoria.api.arquivo.Categoria;
import br.com.condominioauditoria.api.arquivo.SituacaoIndexacao;
import br.com.condominioauditoria.api.config.PropriedadesCondominio;
import br.com.condominioauditoria.api.mensagens.PublicadorIndexacao.ArquivoParaIndexar;
import br.com.condominioauditoria.api.mensagens.PublicadorIndexacao.ArquivosParaIndexar;
import br.com.condominioauditoria.api.modulo.Modulos;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Pedido de indexação vai para rag.indexacao no contrato; a varredura reenvia o que parou e desiste depois de 3. */
class PublicadorIndexacaoTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final Modulos modulos = mock(Modulos.class);
    private final PublicadorIndexacao publicador = new PublicadorIndexacao(rabbit, new ContratoMensagens(), arquivos,
            mock(PlatformTransactionManager.class),
            new PropriedadesCondominio(null, new PropriedadesCondominio.Processamento(15, 3), null, null), modulos,
            Runnable::run);

    @BeforeEach
    void moduloLigado() {
        when(modulos.ligado(any(UUID.class), eq(Modulos.ASSISTENTE))).thenReturn(true);
    }

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

    @Test
    void moduloDesligadoNaoPublicaNemNoAvulsoNemNoLote() {
        Arquivo arquivo = arquivo();
        when(modulos.ligado(arquivo.getCondominioId(), Modulos.ASSISTENTE)).thenReturn(false);
        when(arquivos.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));
        when(arquivos.findAllById(List.of(arquivo.getId()))).thenReturn(List.of(arquivo));

        publicador.aposCommit(new ArquivoParaIndexar(arquivo.getId()));
        publicador.aposCommit(new ArquivosParaIndexar(arquivo.getCondominioId(), List.of(arquivo.getId())));

        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    void varreduraIgnoraCondominioComModuloDesligadoSemGastarTentativa() {
        Arquivo parado = arquivo();
        when(modulos.ligado(parado.getCondominioId(), Modulos.ASSISTENTE)).thenReturn(false);
        when(arquivos.findByIndexacaoSituacaoInAndIndexacaoEnfileiradaEmBeforeOrderByEnviadoEm(anyCollection(), any()))
                .thenReturn(List.of(parado));

        publicador.varrer(Instant.now());

        assertThat(parado.getIndexacaoTentativas()).isEqualTo(1);
        assertThat(parado.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    void lotePublicaUmPedidoPorArquivo() {
        UUID condominio = UUID.randomUUID();
        List<Arquivo> lote = List.of(arquivo(condominio), arquivo(condominio), arquivo(condominio));
        List<UUID> ids = lote.stream().map(Arquivo::getId).toList();
        when(arquivos.findAllById(ids)).thenReturn(lote);

        publicador.aposCommit(new ArquivosParaIndexar(condominio, ids));

        var mensagens = ArgumentCaptor.forClass(Message.class);
        verify(rabbit, times(3)).send(eq(""), eq(Filas.INDEXACAO), mensagens.capture());
        assertThat(mensagens.getAllValues()).extracting(m -> new String(m.getBody(), StandardCharsets.UTF_8))
                .allSatisfy(json -> assertThat(json).contains(condominio.toString()));
    }

    private static Arquivo arquivo() {
        return arquivo(UUID.randomUUID());
    }

    private static Arquivo arquivo(UUID condominio) {
        Arquivo arquivo = new Arquivo(condominio, Categoria.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "b".repeat(64), 10, "application/pdf", "gestor");
        arquivo.novaIndexacao();
        return arquivo;
    }
}
