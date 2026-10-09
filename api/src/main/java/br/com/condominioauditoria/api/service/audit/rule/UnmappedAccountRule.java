package br.com.condominioauditoria.api.service.audit.rule;

import br.com.condominioauditoria.api.model.enums.Severity;

/**
 * Rule "cash flow account without a budget line" (RF-02.7; RF-03.1.6; rule matrix of RF-03.1), per month, in the
 * Condomínio fund. Declarative and versioned: changing the rule means raising {@link #VERSION}. There is a finding, one
 * per account, when some debit of the month falls into "sem linha da PO" (account without a confirmed mapping, or entry
 * without an account). The text describes the fact and what to check, never a cause.
 */
public final class UnmappedAccountRule {

    public static final String CODE = "CONTA_SEM_LINHA_PO";
    public static final String VERSION = "1";
    public static final Severity SEVERITY = Severity.ATENCAO;

    /** Target of the finding: "conta:&lt;code&gt;" or "conta:sem-conta". */
    public static String target(String account) {
        return "conta:" + (account == null ? "sem-conta" : account);
    }

    private UnmappedAccountRule() {
    }
}
