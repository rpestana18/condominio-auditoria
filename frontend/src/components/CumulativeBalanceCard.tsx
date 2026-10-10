import { useState } from "react";
import { useConfirmOperatingFund } from "../api/queries";
import type { Dashboard } from "../api/types";
import { useSession } from "../context";
import { NumberCard } from "./NumberCard";

const HINT = "Saldo guardado no fundo ordinário até esta data. Não é o resultado do mês.";

/** Sixth card on the home screen (RF-05.1a). Without a confirmed operating fund, the Manager sees the suggestion (RF-05.1b). */
export function CumulativeBalanceCard({ dashboard }: { dashboard: Dashboard }) {
  const { condominiumId, can } = useSession();
  const operating = dashboard.operatingFund;
  if (!operating) return null;

  if (operating.confirmed) {
    if (operating.closingBalance === null) {
      return (
        <div className="number-card" title={HINT}>
          <span className="card-title">Saldo acumulado</span>
          <span className="muted">Fundo ordinário não encontrado neste relatório</span>
        </div>
      );
    }
    return (
      <NumberCard
        title="Saldo acumulado"
        value={operating.closingBalance}
        highlight={operating.closingBalance < 0 ? "negative" : undefined}
        hint={`${HINT} Fundo: ${operating.fund}.`}
      />
    );
  }

  if (!can("MANAGER", "ADMIN")) return null;
  return <ConfirmFund condominiumId={condominiumId} dashboard={dashboard} suggested={operating.fundId} />;
}

function ConfirmFund({ condominiumId, dashboard, suggested }: { condominiumId: string; dashboard: Dashboard; suggested: string }) {
  const confirm = useConfirmOperatingFund(condominiumId);
  const [changing, setChanging] = useState(false);
  const [chosen, setChosen] = useState(suggested);
  const suggestedName = dashboard.funds.find((f) => f.fundId === suggested)?.fund ?? "";

  return (
    <div className="number-card">
      <span className="card-title">Saldo acumulado</span>
      {changing ? (
        <select value={chosen} onChange={(e) => setChosen(e.target.value)} aria-label="Fundo ordinário">
          {dashboard.funds.map((f) => (
            <option key={f.fundId} value={f.fundId}>
              {f.fund}
            </option>
          ))}
        </select>
      ) : (
        <span>
          Este é o fundo ordinário? <strong className="fund-name">{suggestedName}</strong>
        </span>
      )}
      <div className="card-actions">
        <button className="button" disabled={confirm.isPending} onClick={() => confirm.mutate(chosen)}>
          Confirmar
        </button>
        {!changing && (
          <button className="link-button" onClick={() => setChanging(true)}>
            Trocar
          </button>
        )}
      </div>
      {confirm.error && <span className="notice error">{confirm.error.message}</span>}
    </div>
  );
}
