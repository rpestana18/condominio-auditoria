package br.com.condominioauditoria.dominio;

/** Categorias de arquivo, na ordem em que aparecem na tela de Arquivos. */
public enum Categoria {
    BALANCETE("Balancetes e fluxos de caixa"),
    EXTRATO("Extratos bancários"),
    PO("Previsão orçamentária"),
    CONTRATO("Contratos"),
    FOLHA("Folha de pagamento"),
    COMPROVANTE("Comprovantes e notas"),
    ATA("Atas de assembleia"),
    CONVENCAO_RI("Convenção e regimento interno"),
    OUTROS("Outros");

    private final String rotulo;

    Categoria(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
