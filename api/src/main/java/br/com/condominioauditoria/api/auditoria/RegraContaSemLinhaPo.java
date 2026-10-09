package br.com.condominioauditoria.api.auditoria;

/**
 * Regra "conta do fluxo sem linha da PO" (RF-02.7; RF-03.1.6; matriz de regras do RF-03.1), por mês, no fundo
 * Condomínio. Declarativa e versionada: mudar a regra é subir {@link #VERSAO}. Há achado, um por conta, quando algum
 * débito do mês fica em "sem linha da PO" (conta sem de-para confirmado, ou lançamento sem conta). O texto descreve o
 * fato e o que verificar, nunca uma causa.
 */
public final class RegraContaSemLinhaPo {

    public static final String CODIGO = "CONTA_SEM_LINHA_PO";
    public static final String VERSAO = "1";
    public static final Severidade SEVERIDADE = Severidade.ATENCAO;

    /** Alvo do achado: "conta:&lt;código&gt;" ou "conta:sem-conta". */
    public static String alvo(String conta) {
        return "conta:" + (conta == null ? "sem-conta" : conta);
    }

    private RegraContaSemLinhaPo() {
    }
}
