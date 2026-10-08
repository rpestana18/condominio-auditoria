package br.com.condominioauditoria.backend.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.storage.Storage;
import br.com.condominioauditoria.backend.mensagens.PublicadorArquivos.ArquivoParaLer;
import br.com.condominioauditoria.backend.mensagens.PublicadorIndexacao.ArquivoParaIndexar;
import br.com.condominioauditoria.backend.modulo.Modulos;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

/**
 * RF-10.3: com o Assistente desligado, o arquivo passa só pelo núcleo (leitura) e nenhum pedido de indexação é feito;
 * o estado de indexação fica nulo. Com ele ligado, envio e reprocesso pedem a indexação.
 */
class ArquivoServiceModuloTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final Modulos modulos = mock(Modulos.class);
    private final ArquivoService servico = new ArquivoService(arquivos, mock(Storage.class), eventos,
            mock(HistoricoCategoriaRepository.class), modulos);

    @BeforeEach
    void preparar() {
        when(arquivos.save(any(Arquivo.class))).thenAnswer(i -> i.getArgument(0));
        when(arquivos.findByCondominioIdAndSha256(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void envioComModuloDesligadoSoLe() throws Exception {
        when(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).thenReturn(false);

        Arquivo arquivo = servico.receber(CONDOMINIO, Categoria.ATA, envio(), "gestor");

        assertThat(arquivo.getIndexacaoSituacao()).isNull();
        assertThat(arquivo.getIndexacaoId()).isNull();
        verify(eventos).publishEvent(new ArquivoParaLer(arquivo.getId()));
        verify(eventos, never()).publishEvent(any(ArquivoParaIndexar.class));
    }

    @Test
    void envioComModuloLigadoPedeAIndexacao() throws Exception {
        when(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).thenReturn(true);

        Arquivo arquivo = servico.receber(CONDOMINIO, Categoria.ATA, envio(), "gestor");

        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
        verify(eventos).publishEvent(new ArquivoParaIndexar(arquivo.getId()));
    }

    @Test
    void reprocessoComModuloDesligadoNaoMexeNoEstadoDeIndexacao() {
        when(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).thenReturn(false);
        Arquivo arquivo = new Arquivo(CONDOMINIO, Categoria.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf", "a".repeat(64), 10,
                "application/pdf", "gestor");
        arquivo.novaIndexacao();
        arquivo.concluirIndexacao(SituacaoIndexacao.INDEXADO, null, 2, 3);
        UUID pedido = arquivo.getIndexacaoId();

        servico.reprocessar(arquivo);

        assertThat(arquivo.getStatus()).isEqualTo(StatusArquivo.PENDENTE);
        assertThat(arquivo.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.INDEXADO); // índice guardado (Q14)
        assertThat(arquivo.getIndexacaoId()).isEqualTo(pedido);
        verify(eventos).publishEvent(new ArquivoParaLer(arquivo.getId()));
        verify(eventos, never()).publishEvent(any(ArquivoParaIndexar.class));
    }

    private static MockMultipartFile envio() {
        return new MockMultipartFile("arquivo", "ata.pdf", "application/pdf",
                ("conteudo " + UUID.randomUUID()).getBytes());
    }
}
