package br.com.condominioauditoria.api.service.audit.rule;

import br.com.condominioauditoria.api.model.enums.Severity;

/**
 * Rule "cash flow account without a budget line" (RF-02.7; RF-03.1.6; rule matrix of RF-03.1), per month, in the
 * Condomínio fund. Declarative and versioned: changing the rule means raising {@link #VERSION}. There is a finding, one
 * per account, when some debit of the month falls into "sem linha da PO" (account without a confirmed mapping, or entry
 * without an account). The text describes the fact and what to check, never a cause.
 */
public final class UnmappedAccountRule {

    public static final String CODE = "ACCOUNT_WITHOUT_BUDGET_LINE";
    public static final String VERSION = "1";
    public static final Severity SEVERITY = Severity.WARNING;

    /** Target of the finding: "account:&lt;code&gt;" or "account:no-account". */
    public static String target(String account) {
        return "account:" + (account == null ? "no-account" : account);
    }

    private UnmappedAccountRule() {
    }
}
