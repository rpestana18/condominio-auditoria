package br.com.condominioauditoria.backend.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.storage.Storage;
import br.com.condominioauditoria.backend.mensagens.PublicadorArquivos.ArquivoParaLer;
import br.com.condominioauditoria.backend.modulo.Modulos;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** RF-01.7: trocar a categoria registra a troca e reprocessa; mesma categoria não faz nada. */
class ArquivoServiceCategoriaTest {

    private final ArquivoRepository arquivos = mock(ArquivoRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final HistoricoCategoriaRepository historico = mock(HistoricoCategoriaRepository.class);
    private final Modulos modulos = mock(Modulos.class);
    private final ArquivoService servico = new ArquivoService(arquivos, mock(Storage.class), eventos, historico,
            modulos);

    private Arquivo arquivo;

    @BeforeEach
    void preparar() {
        arquivo = new Arquivo(UUID.randomUUID(), Categoria.CONTRATO, "fluxo-setembro.pdf",
                "c/CONTRATO/2026/abc-fluxo-setembro.pdf", "a".repeat(64), 100, "application/pdf", "gestor");
        arquivo.concluir(StatusArquivo.CONCLUIDO, "ok", null, null, null, null);
        when(arquivos.save(any(Arquivo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void trocaRegistraHistoricoEReprocessa() {
        UUID processamentoAntes = arquivo.getProcessamentoId();
        String caminhoAntes = arquivo.getCaminho();

        Arquivo salvo = servico.alterarCategoria(arquivo, Categoria.BALANCETE, "gestor@condominio");

        assertThat(salvo.getCategoria()).isEqualTo(Categoria.BALANCETE);
        assertThat(salvo.getStatus()).isEqualTo(StatusArquivo.PENDENTE);
        assertThat(salvo.getProcessamentoId()).isNotEqualTo(processamentoAntes);
        assertThat(salvo.getCaminho()).isEqualTo(caminhoAntes);
        verify(eventos).publishEvent(new ArquivoParaLer(arquivo.getId()));

        var registro = ArgumentCaptor.forClass(HistoricoCategoria.class);
        verify(historico).save(registro.capture());
        assertThat(registro.getValue().getCategoriaAnterior()).isEqualTo(Categoria.CONTRATO);
        assertThat(registro.getValue().getCategoriaNova()).isEqualTo(Categoria.BALANCETE);
        assertThat(registro.getValue().getAlteradoPor()).isEqualTo("gestor@condominio");
        assertThat(registro.getValue().getAlteradoEm()).isNotNull();
    }

    @Test
    void mesmaCategoriaNaoReprocessa() {
        UUID processamentoAntes = arquivo.getProcessamentoId();

        servico.alterarCategoria(arquivo, Categoria.CONTRATO, "gestor");

        assertThat(arquivo.getProcessamentoId()).isEqualTo(processamentoAntes);
        verify(historico, never()).save(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void arquivoEmProcessamentoRecusaTroca() {
        arquivo.iniciarProcessamento();

        assertThatThrownBy(() -> servico.alterarCategoria(arquivo, Categoria.BALANCETE, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O arquivo já está sendo processado");
        assertThat(arquivo.getCategoria()).isEqualTo(Categoria.CONTRATO);
        verify(historico, never()).save(any());
    }
}
