package br.com.condominioauditoria.backend.auditoria;

/**
 * Estados do achado. ABERTO e NAO_SE_APLICA_MAIS são do sistema: o recálculo abre, encerra (a condição deixou de
 * existir, Q27) e reabre. JUSTIFICADO, RESOLVIDO e FALSO_POSITIVO são marcações humanas do RF-02.8, que entram com a
 * tela de achados; achado marcado por pessoa nunca muda de estado pelo recálculo.
 */
public enum EstadoAchado {
    ABERTO, NAO_SE_APLICA_MAIS, JUSTIFICADO, RESOLVIDO, FALSO_POSITIVO;

    /** Estado dado pelo sistema (o recálculo pode mudar). */
    public boolean doSistema() {
        return this == ABERTO || this == NAO_SE_APLICA_MAIS;
    }
}
