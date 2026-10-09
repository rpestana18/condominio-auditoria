package br.com.condominioauditoria.api.processamento;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.arquivo.ArquivoRepository;
import br.com.condominioauditoria.api.arquivo.SituacaoIndexacao;
import br.com.condominioauditoria.api.mensagens.ResultadoIndexacao;
import br.com.condominioauditoria.api.modulo.RegistroUso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o estado da indexação informado pelo rag. Resultado de um pedido antigo (outro indexacaoId) é descartado: o
 * usuário pode ter reprocessado o arquivo no meio do caminho. Também descarta se o condomínio não bate com o do
 * arquivo (mensagem trocada nunca mexe em arquivo de outro condomínio).
 *
 * Cada arquivo que passa a Indexado gera um registro de uso "indexacao" (RF-09.7) com 1 arquivo e as páginas lidas.
 * Um INDEXADO repetido do mesmo pedido (reentrega da fila) não conta de novo.
 */
@Service
public class IndexacaoArquivoService {

    private static final Logger log = LoggerFactory.getLogger(IndexacaoArquivoService.class);

    private final ArquivoRepository arquivos;
    private final RegistroUso registroUso;

    IndexacaoArquivoService(ArquivoRepository arquivos, RegistroUso registroUso) {
        this.arquivos = arquivos;
        this.registroUso = registroUso;
    }

    /** Devolve true se o resultado foi aplicado; false se foi descartado. */
    @Transactional
    public boolean aplicar(ResultadoIndexacao resultado) {
        Arquivo arquivo = arquivos.findById(resultado.arquivoId()).orElse(null);
        if (arquivo == null || !arquivo.ehDaIndexacao(resultado.indexacaoId())
                || !arquivo.getCondominioId().equals(resultado.condominioId())) {
            log.info("Resultado de indexação descartado: arquivo {} foi apagado ou reindexado depois deste pedido ({})",
                    resultado.arquivoId(), resultado.indexacaoId());
            return false;
        }
        boolean jaIndexado = arquivo.getIndexacaoSituacao() == SituacaoIndexacao.INDEXADO;
        switch (resultado.situacao()) {
            case INDEXANDO -> arquivo.iniciarIndexacao();
            case INDEXADO -> arquivo.concluirIndexacao(SituacaoIndexacao.INDEXADO, null, resultado.paginas(),
                    resultado.trechos());
            case SEM_TEXTO -> arquivo.concluirIndexacao(SituacaoIndexacao.SEM_TEXTO, resultado.motivo(),
                    resultado.paginas(), resultado.trechos());
            case RETIRADO -> arquivo.concluirIndexacao(SituacaoIndexacao.RETIRADO, null, resultado.paginas(),
                    resultado.trechos());
            case ERRO -> arquivo.concluirIndexacao(SituacaoIndexacao.ERRO, resultado.motivo(), resultado.paginas(),
                    resultado.trechos());
        }
        if (resultado.situacao() == ResultadoIndexacao.Situacao.INDEXADO && !jaIndexado) {
            registroUso.indexacao(arquivo.getCondominioId(), resultado.paginas(), resultado.modeloEmbeddings());
        }
        if (resultado.situacao() != ResultadoIndexacao.Situacao.INDEXANDO) {
            log.info("Indexação de {}: {} ({} trechos)", arquivo.getNomeOriginal(), resultado.situacao(),
                    resultado.trechos());
        }
        return true;
    }
}
