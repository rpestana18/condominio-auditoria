package br.com.condominioauditoria.api.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.arquivo.ArquivoRepository;
import br.com.condominioauditoria.api.arquivo.Categoria;
import br.com.condominioauditoria.api.arquivo.SituacaoIndexacao;
import br.com.condominioauditoria.api.mensagens.PublicadorIndexacao.ArquivosParaIndexar;
import br.com.condominioauditoria.api.modulo.ModuloAlterado;
import br.com.condominioauditoria.api.modulo.Modulos;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Ligar o Assistente põe todos os arquivos na fila de indexação (RF-10.4); desligar não mexe em nada (RF-10.5). */
class ReindexacaoAoLigarModuloTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final ReindexacaoAoLigarModulo reindexacao = new ReindexacaoAoLigarModulo(arquivos, eventos);

    @Test
    void ligarPedeAIndexacaoDeTodosOsArquivosDoCondominio() {
        List<Arquivo> quarenta = IntStream.range(0, 40).mapToObj(i -> arquivo()).toList();
        Arquivo jaIndexado = quarenta.getFirst();
        jaIndexado.novaIndexacao();
        jaIndexado.concluirIndexacao(SituacaoIndexacao.INDEXADO, null, 3, 5);
        UUID pedidoAntigo = jaIndexado.getIndexacaoId();
        when(arquivos.findByCondominioIdOrderByEnviadoEmDesc(CONDOMINIO)).thenReturn(quarenta);

        reindexacao.aoAlterar(new ModuloAlterado(CONDOMINIO, Modulos.ASSISTENTE, true, "admin"));

        assertThat(quarenta).allSatisfy(a -> {
            assertThat(a.getIndexacaoSituacao()).isEqualTo(SituacaoIndexacao.NA_FILA);
            assertThat(a.getIndexacaoId()).isNotNull();
        });
        assertThat(jaIndexado.getIndexacaoId()).isNotEqualTo(pedidoAntigo);
        var lote = ArgumentCaptor.forClass(ArquivosParaIndexar.class);
        verify(eventos).publishEvent(lote.capture());
        assertThat(lote.getValue().condominioId()).isEqualTo(CONDOMINIO);
        assertThat(lote.getValue().arquivoIds()).containsExactlyElementsOf(quarenta.stream().map(Arquivo::getId).toList());
    }

    @Test
    void desligarNaoMexeNoIndiceNemPublica() {
        reindexacao.aoAlterar(new ModuloAlterado(CONDOMINIO, Modulos.ASSISTENTE, false, "admin"));

        verifyNoInteractions(arquivos, eventos);
    }

    @Test
    void condominioSemArquivosNaoPublica() {
        when(arquivos.findByCondominioIdOrderByEnviadoEmDesc(CONDOMINIO)).thenReturn(List.of());

        reindexacao.aoAlterar(new ModuloAlterado(CONDOMINIO, Modulos.ASSISTENTE, true, "admin"));

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    private static Arquivo arquivo() {
        return new Arquivo(CONDOMINIO, Categoria.ATA, "ata.pdf", "c/ATA/2026/" + UUID.randomUUID() + "-ata.pdf",
                UUID.randomUUID().toString().replace("-", "").repeat(2), 10, "application/pdf", "gestor");
    }
}
