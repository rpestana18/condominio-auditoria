import { useState } from "react";
import { useDecideUtilityAccount, useUtilityAccounts } from "../api/queries";
import type { UtilityAccount, UtilityType } from "../api/types";
import { useSession } from "../context";
import { formatDateTime, formatMoney } from "../format";

const LABELS: Record<UtilityType, string> = { WATER: "Água e esgoto", ELECTRICITY: "Energia", GAS: "Gás" };
const TYPES: UtilityType[] = ["WATER", "ELECTRICITY", "GAS"];

/**
 * Cash flow accounts of each utility (RF-12.1). The system suggests by the account name; the Manager or the Admin
 * confirms, changes or rejects. Only confirmed accounts go into the "Concessionárias" chart on the home screen.
 */
export function UtilityAccounts() {
  const { condominiumId, can } = useSession();
  const canDecide = can("MANAGER", "ADMIN");
  const { data: accounts = [], isLoading, error } = useUtilityAccounts(condominiumId);
  const [showAll, setShowAll] = useState(false);
  const shown = (accounts ?? []).filter((a) => showAll || a.status !== "NONE");

  return (
    <>
      <header className="page-title">
        <h1>Contas das concessionárias</h1>
      </header>
      <p className="muted">
        O gráfico "Concessionárias" da tela inicial soma só as contas confirmadas aqui. O código da conta muda de uma
        administradora para outra, então cada conta é confirmada uma vez.
      </p>
      {isLoading && <p className="notice">Carregando…</p>}
      {error && <p className="notice error">{error.message}</p>}
      <label className="checkbox">
        <input type="checkbox" checked={showAll} onChange={(e) => setShowAll(e.target.checked)} /> Mostrar todas as contas com saídas
      </label>
      {!isLoading && shown.length === 0 && <p className="notice">Nenhuma conta sugerida. Marque a opção acima para ver todas.</p>}
      {shown.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Conta</th>
              <th className="number">Saídas nos fluxos</th>
              <th>Situação</th>
              {canDecide && <th>Decisão</th>}
            </tr>
          </thead>
          <tbody>
            {shown.map((a) => (
              <AccountRow key={a.accountCode} account={a} canDecide={canDecide} />
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

function statusText(a: UtilityAccount): string {
  switch (a.status) {
    case "CONFIRMED":
      return `${LABELS[a.utilityType!]} (confirmada)`;
    case "REJECTED":
      return "Não é de concessionária";
    case "SUGGESTED":
      return `Sugestão: ${LABELS[a.suggested!]}`;
    case "NONE":
      return "—";
  }
}

function AccountRow({ account: a, canDecide }: { account: UtilityAccount; canDecide: boolean }) {
  const { condominiumId } = useSession();
  const decide = useDecideUtilityAccount(condominiumId);
  const current = a.utilityType ?? a.suggested ?? "";
  const [choice, setChoice] = useState<UtilityType | "">(current);
  const save = (utilityType: UtilityType | null) => decide.mutate({ accountCode: a.accountCode, utilityType });

  return (
    <tr>
      <td>
        {a.accountCode} {a.accountName}
      </td>
      <td className="number">{formatMoney(a.debits)}</td>
      <td>
        {statusText(a)}
        {a.decidedBy && a.decidedAt && (
          <span className="muted">
            {" "}
            · {a.decidedBy}, {formatDateTime(a.decidedAt)}
          </span>
        )}
      </td>
      {canDecide && (
        <td>
          <select value={choice} onChange={(e) => setChoice(e.target.value as UtilityType | "")} aria-label={`Concessionária da conta ${a.accountCode}`}>
            <option value="">Escolha</option>
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {LABELS[t]}
              </option>
            ))}
          </select>{" "}
          <button className="button" disabled={!choice || decide.isPending} onClick={() => choice && save(choice)}>
            Confirmar
          </button>{" "}
          {a.status !== "REJECTED" && (
            <button className="button secondary" disabled={decide.isPending} onClick={() => save(null)}>
              Não é de concessionária
            </button>
          )}
          {decide.error && <span className="notice error">{decide.error.message}</span>}
        </td>
      )}
    </tr>
  );
}
