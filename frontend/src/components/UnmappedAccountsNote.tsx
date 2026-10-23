import { Link } from "react-router";
import { formatInteger, formatMoney } from "../format";
import { accountsText, mappingLink, type PendingSteps } from "./pendingSteps";

/** How many accounts the collapsed panel lists; the full list is on the "De-para" screen. */
const LISTED_ACCOUNTS = 10;

interface Props {
  steps: PendingSteps;
  /** Lists the largest accounts in a collapsed panel (off where the screen already lists them). */
  withAccounts?: boolean;
}

/**
 * Short line about the accounts without a confirmed mapping (RF-03.1.10), built from the API fields instead of the
 * warning text: "88 contas do extrato ainda sem linha da PO (R$ 2.247.648,60) · Confirmar o de-para", with the 10
 * largest accounts in a collapsed panel. The order is the API's (largest amount first).
 */
export function UnmappedAccountsNote({ steps, withAccounts = true }: Props) {
  if (steps.unmappedAccounts === 0) return null;
  const listed = steps.accounts.slice(0, LISTED_ACCOUNTS);
  return (
    // Inside a clickable row: the links and the panel do not select the row
    <div className="mapping-note" onClick={(e) => e.stopPropagation()}>
      <span>
        {accountsText(steps.unmappedAccounts)} do extrato ainda sem linha da PO
        {steps.unmappedAmount != null && ` (${formatMoney(steps.unmappedAmount)})`}
      </span>{" "}
      · <Link to={mappingLink(steps.budgetId)}>Confirmar o de-para</Link>
      {withAccounts && listed.length > 0 && (
        <details className="mapping-accounts">
          <summary>
            {steps.accounts.length > LISTED_ACCOUNTS ? `Ver as ${LISTED_ACCOUNTS} maiores` : "Ver as contas"}
          </summary>
          <table className="table compact">
            <thead>
              <tr>
                <th>Conta</th>
                <th className="number">Valor</th>
                <th className="number">Lançamentos</th>
              </tr>
            </thead>
            <tbody>
              {listed.map((a, i) => (
                <tr key={a.account ?? `no-account-${i}`}>
                  <td>{a.account == null ? "lançamentos sem conta" : `${a.account}${a.name ? ` ${a.name}` : ""}`}</td>
                  <td className="number">{formatMoney(a.amount)}</td>
                  <td className="number">{formatInteger(a.entries)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Link to={mappingLink(steps.budgetId)}>Ver todas no De-para</Link>
        </details>
      )}
    </div>
  );
}
