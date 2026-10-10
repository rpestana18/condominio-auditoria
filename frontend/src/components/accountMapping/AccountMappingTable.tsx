import type { MappedAccount } from "../../api/types";
import { formatMoney } from "../../format";
import { sourceLabel } from "../budgetVsActual/labels";
import { AccountMappingStatusBadge } from "./AccountMappingStatusBadge";

interface Props {
  accounts: MappedAccount[];
  /** Only the Admin gets selection and editing; for everyone else the table is read only. */
  editing?: {
    selected: Set<string>;
    onSelect: (account: string, checked: boolean) => void;
    onSelectAll: (checked: boolean) => void;
    onEdit: (account: MappedAccount) => void;
  };
}

/** Cash flow accounts with the target, the status, the source and the suggestion reason (RF-03.1.13). */
export function AccountMappingTable({ accounts, editing }: Props) {
  const all = editing && accounts.length > 0 && accounts.every((a) => editing.selected.has(a.account));
  return (
    <div className="scroll">
      <table className="table">
        <thead>
          <tr>
            {editing && (
              <th>
                <input type="checkbox" aria-label="Selecionar todas" checked={!!all} onChange={(e) => editing.onSelectAll(e.target.checked)} />
              </th>
            )}
            <th>Conta do fluxo</th>
            <th className="number">Lançamentos</th>
            <th className="number">Débitos no exercício</th>
            <th>Destino</th>
            <th>Estado</th>
            <th>Origem e motivo</th>
            {editing && <th aria-label="Ações" />}
          </tr>
        </thead>
        <tbody>
          {accounts.map((a) => (
            <tr key={a.account} className={editing?.selected.has(a.account) ? "selected" : undefined}>
              {editing && (
                <td>
                  <input
                    type="checkbox"
                    aria-label={`Selecionar a conta ${a.account}`}
                    checked={editing.selected.has(a.account)}
                    onChange={(e) => editing.onSelect(a.account, e.target.checked)}
                  />
                </td>
              )}
              <td>
                <strong>{a.account}</strong> {a.name}
              </td>
              <td className="number">{a.ledgerEntries}</td>
              <td className="number">{formatMoney(a.debits)}</td>
              <td>{a.target?.text ?? <span className="muted">—</span>}</td>
              <td>
                <AccountMappingStatusBadge status={a.status} />
                {a.sameAsPreviousVersion && <span className="badge neutral">igual à versão anterior</span>}
              </td>
              <td className="muted">
                {a.source && <span>{sourceLabel[a.source]}</span>}
                {a.reason && <small className="note">{a.reason}</small>}
              </td>
              {editing && (
                <td>
                  <button className="link-button" onClick={() => editing.onEdit(a)}>
                    Alterar
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
