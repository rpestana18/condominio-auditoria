import { useCashFlowEntries, type CashFlowEntryFilter } from "../../api/cashFlowQueries";
import type { CashFlowMonthly, CashFlowRowCode } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatMoney, formatMonth, shortHash } from "../../format";

interface Props {
  cashFlow: CashFlowMonthly;
  filter: CashFlowEntryFilter;
  title: string;
  onChange: (filter: CashFlowEntryFilter, title: string) => void;
  onClose: () => void;
}

/** Blocks the list can filter by (the same of the table). */
const BLOCK_LABEL: Partial<Record<CashFlowRowCode, string>> = {
  BUDGET_LINE: "qualquer linha da PO",
  SEPARATE_APPORTIONMENT: "rateio à parte (concessionárias)",
  WITHOUT_BUDGET_LINE: "sem linha da PO",
  TO_REALLOCATE: "a realocar",
  ADJUSTMENTS: "ajustes",
  BY_ACCOUNT: "por conta do extrato",
  CONDO_FEE: "cota recebida",
  OTHER_INCOME: "outras receitas",
  OTHER_CREDITS: "outros créditos",
  TRANSFERS_IN: "transferências recebidas",
  TRANSFERS_OUT: "transferências enviadas",
};

/**
 * List of entries of RF-13.11 ("este gasto é de qual linha?"): date, memo, supplier, cash flow account, budget line or
 * block, amount, file and page. Opens from a cell of the table or by itself, with filters by month, line and block.
 */
export function CashFlowEntriesPanel({ cashFlow, filter, title, onChange, onClose }: Props) {
  const { condominiumId } = useSession();
  const { data, isLoading, error } = useCashFlowEntries(condominiumId, filter);
  const lines = cashFlow.outflowGroups.flatMap((g) => g.lines).filter((l) => l.lineId);
  const change = (changes: Partial<CashFlowEntryFilter>) => onChange({ ...filter, ...changes }, "Lançamentos");
  return (
    <section className="block evidence-panel" aria-label="Lançamentos">
      <div className="block-title">
        <h2>
          {title}
          {filter.month ? ` · ${formatMonth(filter.month)}` : " · período"}
        </h2>
        <button className="link-button" onClick={onClose}>
          Fechar
        </button>
      </div>
      <div className="filters">
        <label>
          Mês
          <select value={filter.month ?? ""} onChange={(e) => change({ month: e.target.value || null })}>
            <option value="">Todo o período</option>
            {cashFlow.months
              .filter((m) => m.status !== "NO_CASH_FLOW")
              .map((m) => (
                <option key={m.month} value={m.month}>
                  {formatMonth(m.month)}
                </option>
              ))}
          </select>
        </label>
        <label>
          Linha da PO
          <select value={filter.line ?? ""} onChange={(e) => change({ line: e.target.value || null, block: null, account: null })}>
            <option value="">Todas</option>
            {lines.map((l) => (
              <option key={l.lineId} value={l.lineId ?? ""}>
                {l.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Bloco
          <select
            value={filter.block ?? ""}
            onChange={(e) => change({ block: (e.target.value || null) as CashFlowRowCode | null, line: null, account: null })}
          >
            <option value="">Todos</option>
            {Object.entries(BLOCK_LABEL).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      {isLoading && <p className="notice">Carregando…</p>}
      {error && <p className="notice error">{error.message}</p>}
      {data && (
        <>
          <p className="muted">
            {data.count} lançamento(s) · total {formatMoney(data.total)}
            {data.message ? ` · ${data.message}` : ""}
          </p>
          <div className="scroll">
            <table className="table compact">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Histórico</th>
                  <th>Favorecido</th>
                  <th>Conta do extrato</th>
                  <th>Linha da PO ou bloco</th>
                  {cashFlow.allFunds && <th>Fundo</th>}
                  <th className="number">Valor</th>
                  <th>Arquivo e página</th>
                </tr>
              </thead>
              <tbody>
                {data.entries.map((e) => (
                  <tr key={e.entryId}>
                    <td>{formatDate(e.date)}</td>
                    <td>{e.memo}</td>
                    <td>{e.supplier ?? ""}</td>
                    <td>{[e.account, e.accountName].filter(Boolean).join(" ")}</td>
                    <td>{e.lineCode ? `${e.lineCode} ${e.lineDescription ?? ""}` : (e.destinationLabel ?? "")}</td>
                    {cashFlow.allFunds && <td>{e.fund ?? ""}</td>}
                    <td className={`number ${e.direction === "IN" ? "" : "above"}`}>
                      {e.direction === "IN" ? formatMoney(e.amount) : `−${formatMoney(e.amount)}`}
                    </td>
                    <td title={e.sha256 ? `SHA-256 ${e.sha256}` : undefined}>
                      {e.fileName ?? "extrato"}
                      {e.page != null && `, p. ${e.page}`}
                      {e.sha256 && <small className="muted"> {shortHash(e.sha256)}</small>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
