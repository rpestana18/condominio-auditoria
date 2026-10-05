package br.com.condominioauditoria.backend.modulo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de uso dos módulos (RF-09.7; ADR 0003, Decisão 4). Grava uma linha por operação e resume por período.
 * Nunca recebe texto de documento, texto da busca nem chave.
 */
@Service
public class RegistroUso {

    /** Meses e datas do relatório de uso seguem o horário de Brasília. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final UsoModuloRepository usos;

    RegistroUso(UsoModuloRepository usos) {
        this.usos = usos;
    }

    /** Um arquivo indexado pelo rag (arquivos = 1). Com modelo de embeddings = LOCAL; sem vetores = DESLIGADO. */
    @Transactional
    public void indexacao(UUID condominioId, Integer paginas, String modeloEmbeddings) {
        ModoIa modo = modeloEmbeddings == null || modeloEmbeddings.isBlank() ? ModoIa.DESLIGADO : ModoIa.LOCAL;
        usos.save(new UsoModulo(condominioId, Modulos.ASSISTENTE, FuncaoUso.INDEXACAO, null, Instant.now(), modo, null,
                modo == ModoIa.LOCAL ? modeloEmbeddings : null, null, null, 1, paginas, null));
    }

    /**
     * Uma chamada a buscar_documentos pelo MCP. buscaHibrida = o rag usou os embeddings locais (LOCAL); só por
     * palavra = DESLIGADO. O modelo da busca não vem na resposta do rag, então fica nulo.
     */
    @Transactional
    public void chamadaMcp(UUID condominioId, String usuario, boolean buscaHibrida) {
        usos.save(new UsoModulo(condominioId, Modulos.ASSISTENTE, FuncaoUso.CHAMADA_MCP,
                Objects.requireNonNull(usuario), Instant.now(), buscaHibrida ? ModoIa.LOCAL : ModoIa.DESLIGADO, null,
                null, null, null, null, null, null));
    }

    /** Uso do período, de inicio a fim (dias inteiros em Brasília, fim incluído), por mês e por função. */
    @Transactional(readOnly = true)
    public ResumoUso resumo(UUID condominioId, LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        List<TotalUso> porMes = usos.totaisPorMes(condominioId, inicioDoDia(inicio), inicioDoDia(fim.plusDays(1)))
                .stream()
                .map(l -> new TotalUso(l.getMes(), l.getModulo(), FuncaoUso.deCodigo(l.getFuncao()), l.getQuantidade(),
                        l.getTokensEntrada(), l.getTokensSaida(), l.getArquivos(), l.getPaginas()))
                .toList();
        return new ResumoUso(condominioId, inicio, fim, TotalUso.somarPorFuncao(porMes), porMes);
    }

    public record ResumoUso(UUID condominioId, LocalDate inicio, LocalDate fim, List<TotalUso> porFuncao,
            List<TotalUso> porMes) {
    }

    static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new PedidoInvalidoException("Informe o início e o fim do período");
        }
        if (inicio.isAfter(fim)) {
            throw new PedidoInvalidoException("O início do período (" + inicio + ") é depois do fim (" + fim + ")");
        }
    }

    static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(FUSO).toInstant();
    }
}
