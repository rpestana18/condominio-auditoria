package br.com.condominioauditoria.api.model.enums;

/** File categories, in the order they appear on the Files screen. */
public enum FileCategory {
    BALANCETE("Balancetes e fluxos de caixa"),
    EXTRATO("Extratos bancários"),
    PO("Previsão orçamentária"),
    CONTRATO("Contratos"),
    FOLHA("Folha de pagamento"),
    COMPROVANTE("Comprovantes e notas"),
    ATA("Atas de assembleia"),
    CONVENCAO_RI("Convenção e regimento interno"),
    OUTROS("Outros");

    private final String label;

    FileCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
