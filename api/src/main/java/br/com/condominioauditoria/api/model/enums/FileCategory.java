package br.com.condominioauditoria.api.model.enums;

/** File categories, in the order they appear on the Files screen. */
public enum FileCategory {
    TRIAL_BALANCE("Balancetes e fluxos de caixa"),
    BANK_STATEMENT("Extratos bancários"),
    PO("Previsão orçamentária"),
    CONTRACT("Contratos"),
    PAYROLL("Folha de pagamento"),
    RECEIPT("Comprovantes e notas"),
    MINUTES("Atas de assembleia"),
    BYLAWS("Convenção e regimento interno"),
    OTHER("Outros");

    private final String label;

    FileCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
