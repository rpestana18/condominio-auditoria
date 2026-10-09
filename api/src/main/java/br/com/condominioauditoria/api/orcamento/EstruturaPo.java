package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Total, grupos e linhas da PO na ordem do documento (mesma regra da conferência do rag): as linhas de um grupo são
 * as que vêm depois da linha GRUPO, e não as que têm o prefixo do código, que pode estar repetido. Função pura.
 */
public record EstruturaPo(LinhaPo total, List<Grupo> grupos, List<LinhaPo> semGrupo) {

    public record Grupo(LinhaPo linha, List<LinhaPo> linhas, boolean fundos) {

        public BigDecimal somaDasLinhas() {
            return linhas.stream().map(LinhaPo::getOrcado).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Impresso − soma das linhas. Zero quando bate. */
        public BigDecimal diferenca() {
            return linha.getOrcado().subtract(somaDasLinhas());
        }

        public String nome() {
            return linha.getCodigoImpresso() + " " + linha.getDescricao();
        }
    }

    public static EstruturaPo de(List<LinhaPo> linhasEmQualquerOrdem) {
        List<LinhaPo> linhas = linhasEmQualquerOrdem.stream().sorted(Comparator.comparingInt(LinhaPo::getOrdem)).toList();
        LinhaPo total = null;
        List<Grupo> grupos = new ArrayList<>();
        List<LinhaPo> semGrupo = new ArrayList<>();
        Grupo atual = null;
        for (LinhaPo l : linhas) {
            switch (l.getTipo()) {
                case TOTAL -> total = total == null ? l : total;
                case GRUPO -> {
                    atual = new Grupo(l, new ArrayList<>(), ehFundos(l));
                    grupos.add(atual);
                }
                case LINHA -> {
                    if (atual == null) {
                        semGrupo.add(l);
                    } else {
                        atual.linhas().add(l);
                    }
                }
            }
        }
        // Só o primeiro grupo de fundos conta como fundos, como no rag
        List<Grupo> marcados = new ArrayList<>();
        boolean achou = false;
        for (Grupo g : grupos) {
            boolean fundos = g.fundos() && !achou;
            achou |= fundos;
            marcados.add(new Grupo(g.linha(), List.copyOf(g.linhas()), fundos));
        }
        return new EstruturaPo(total, List.copyOf(marcados), List.copyOf(semGrupo));
    }

    public Optional<Grupo> fundos() {
        return grupos.stream().filter(Grupo::fundos).findFirst();
    }

    public List<Grupo> gruposSemFundos() {
        return grupos.stream().filter(g -> !g.fundos()).toList();
    }

    /** Previsto do mês usado nos cálculos: soma das linhas dos grupos que não são fundos (RF-03.1.2, Q29). */
    public BigDecimal previstoMesPelasLinhas() {
        return gruposSemFundos().stream().map(Grupo::somaDasLinhas).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total impresso − fundos impressos. Vazio sem linha de total ou sem grupo de fundos. */
    public Optional<BigDecimal> previstoMesImpresso() {
        if (total == null || fundos().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(total.getOrcado().subtract(fundos().get().linha().getOrcado()));
    }

    public BigDecimal somaDosSubtotaisImpressos() {
        return grupos.stream().map(g -> g.linha().getOrcado()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal somaDosSubtotaisImpressosSemFundos() {
        return gruposSemFundos().stream().map(g -> g.linha().getOrcado()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Grupo de fundos: a coluna de conta (ou a descrição) começa por "Fundos", e não por "Subtotal". */
    private static boolean ehFundos(LinhaPo grupo) {
        String conta = grupo.getContaTexto() == null ? "" : normalizar(grupo.getContaTexto());
        return conta.startsWith("fundos")
                || (!conta.startsWith("subtotal") && normalizar(grupo.getDescricao()).startsWith("fundos"));
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
