import type { ReactNode } from "react";
import { Link } from "react-router";

/**
 * Empty state of the screens that work on a fiscal year (budget vs actual, balance origin, funds, indicators) while no
 * budget (PO) was confirmed yet. Shown instead of asking the API, which needs a confirmed budget to pick the fiscal year.
 */
export function NoConfirmedBudget({ what, children }: { what: string; children?: ReactNode }) {
  // `what` completes "esta tela mostra ...", e.g. "os indicadores do exercício"
  return (
    <section className="block no-numbers">
      <h2>Nenhuma PO confirmada ainda</h2>
      <p>
        Envie a PO em Arquivos e confirme-a em "PO aprovada". Depois da confirmação, esta tela mostra {what}.
      </p>
      {children}
      <Link className="link-button" to="/arquivos">
        Ir para Arquivos
      </Link>{" "}
      <Link className="link-button" to="/previsoes">
        Ver as POs enviadas
      </Link>
    </section>
  );
}
