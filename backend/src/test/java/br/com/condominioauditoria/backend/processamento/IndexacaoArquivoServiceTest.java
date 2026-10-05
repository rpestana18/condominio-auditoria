package br.com.condominioauditoria.backend.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.arquivo.SituacaoIndexacao;
import br.com.condominioauditoria.backend.mensagens.ResultadoIndexacao;
import br.com.condominioauditoria.backend.mensagens.ResultadoIndexacao.Situacao;
import br.com.condominioauditoria.backend.modulo.RegistroUso;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** O estado da indexação só muda com resultado do pedido atual, do mesmo condomínio, e na ordem certa. */
class IndexacaoArquivoServiceTest {

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final RegistroUso registroUso = mock(RegistroUso.class);
    private final IndexacaoArquivoService servico = new IndexacaoArquivoService(arquivos, registroUso);
    private Arquivo arquivo;

    @BeforeEach
    void preparar() {
        arquivo = new Arquivo(UUID.randomUUID(), Categoria.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf", "a".repeat(64), 10,
                "application/pdf", "gestor");
        arquivo.novaIndexacao();
        when(arquivos.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));
    }

    @Test
    void arquivoNovoNasceSemIndexacao() {
        var novo = new Arquivo(UUID.randomUUID(), Categoria.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf", "a".repeat(64), 10,
                "application/pdf", "gestor");
        assertThat(novo.getIndexacaoSituacao()).isNull();
        assertThat(novo.getIndexacaoId()).isNull();
    }

    @Test
    void indexadoRegistraUsoUmaVezMesmoComReentrega() {
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXANDO, null, null, null));
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXADO, null, 12, 15));
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXADO, null, 12, 15)); // reentrega

        verify(registroUso, times(1)).indexacao(arquivo.getCondominioId(), 12, "bge-m3");
    }

    @Test
    void semTextoErroEDescartadoNaoRegistramUso() {
        UUID velho = arquivo.getIndexacaoId();
        arquivo.novaIndexacao();
        servico.aplicar(resultado(velho, Situacao.INDEXADO, null, 3, 4));
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.SEM_TEXTO, "sem texto", 3, 0));
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.ERRO, "falhou", null, null));

        verifyNoInteractions(registroUso);
    }

    @Test
    void pedidoNovoNasceNaFila() {
        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
        assertThat(arquivo.getIndexacaoId()).isNotNull();
        assertThat(arquivo.getIndexacaoTentativas()).isEqualTo(1);
    }

    @Test
    void gravaIndexandoEDepoisIndexado() {
        assertThat(servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXANDO, null, null, null))).isTrue();
        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.INDEXANDO);

        assertThat(servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXADO, null, 12, 15))).isTrue();
        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.INDEXADO);
        assertThat(arquivo.getIndexacaoPaginas()).isEqualTo(12);
        assertThat(arquivo.getIndexacaoTrechos()).isEqualTo(15);
        assertThat(arquivo.getIndexacaoMotivo()).isNull();
    }

    @Test
    void semTextoGuardaOMotivo() {
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.SEM_TEXTO, "PDF digitalizado sem texto", 3, 0));

        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.SEM_TEXTO);
        assertThat(arquivo.getIndexacaoMotivo()).isEqualTo("PDF digitalizado sem texto");
    }

    @Test
    void resultadoDePedidoVelhoEhDescartado() {
        UUID velho = arquivo.getIndexacaoId();
        arquivo.novaIndexacao(); // reprocessado no meio do caminho

        assertThat(servico.aplicar(resultado(velho, Situacao.INDEXADO, null, 12, 15))).isFalse();
        assertThat(servico.aplicar(resultado(velho, Situacao.ERRO, "falhou", null, null))).isFalse();

        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
        assertThat(arquivo.getIndexacaoTrechos()).isNull();
    }

    @Test
    void novoPedidoLimpaOResultadoAnterior() {
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.ERRO, "rag caiu", null, null));
        UUID anterior = arquivo.getIndexacaoId();

        arquivo.novaIndexacao();

        assertThat(arquivo.getIndexacaoId()).isNotEqualTo(anterior);
        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
        assertThat(arquivo.getIndexacaoMotivo()).isNull();
    }

    @Test
    void indexandoAtrasadoNaoDesfazOResultado() {
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXADO, null, 1, 2));
        servico.aplicar(resultado(arquivo.getIndexacaoId(), Situacao.INDEXANDO, null, null, null));

        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.INDEXADO);
    }

    @Test
    void resultadoComOutroCondominioEhDescartado() {
        var trocado = new ResultadoIndexacao(1, arquivo.getIndexacaoId(), arquivo.getId(), UUID.randomUUID(),
                Situacao.INDEXADO, null, 1, 1, "bge-m3", "1");

        assertThat(servico.aplicar(trocado)).isFalse();
        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
    }

    @Test
    void arquivoInexistenteEhDescartado() {
        var outro = new ResultadoIndexacao(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Situacao.INDEXADO, null, 1, 1, "bge-m3", "1");
        assertThat(servico.aplicar(outro)).isFalse();
    }

    private ResultadoIndexacao resultado(UUID indexacaoId, Situacao situacao, String motivo, Integer paginas,
            Integer trechos) {
        return new ResultadoIndexacao(1, indexacaoId, arquivo.getId(), arquivo.getCondominioId(), situacao, motivo,
                paginas, trechos, trechos == null ? null : "bge-m3", trechos == null ? null : "1");
    }
}
