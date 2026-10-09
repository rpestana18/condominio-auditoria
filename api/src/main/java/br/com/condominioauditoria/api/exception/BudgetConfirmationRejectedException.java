package br.com.condominioauditoria.api.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Budget confirmation rejected, with every reason at once (422) or by a conflict with another budget (409). */
public class BudgetConfirmationRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> reasons;

    public BudgetConfirmationRejectedException(HttpStatus status, List<String> reasons) {
        super(String.join(" ", reasons));
        this.status = status;
        this.reasons = List.copyOf(reasons);
    }

    public HttpStatus status() {
        return status;
    }

    public List<String> reasons() {
        return reasons;
    }
}
