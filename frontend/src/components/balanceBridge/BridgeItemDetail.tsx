import type { BridgeFund, BridgeItem, BridgeLine, SpendingProfile } from "../../api/types";
import { formatDifference, formatMoney } from "../../format";
import { spendingProfileLabel } from "../budgetVsActual/labels";
import { lineTarget, partTarget, type OpenBridgeEvidence } from "./bridgeEvidence";

interface Props {
  item: BridgeItem;
  /** Null when the entries cannot be opened for this view (e.g. another fund, or a range that is not one month). */
  onOpenEvidence: OpenBridgeEvidence | null;
}

/** How many lines to show on each side ("para mais" and "para menos"); the rest stays in the full list. */
const TOP = 10;

/**
 * Profiles marked in the "Economia nas linhas" list (Q52, 28/10/2026): the occasional and annual lines are savings too,
 * with a discreet "eventual" or "anual" next to the line. The recurring and seasonal ones carry no mark there.
 */
const MARKED_IN_SAVINGS: ReadonlySet<SpendingProfile> = new Set<SpendingProfile>(["OCCASIONAL", "ANNUAL"]);

/** The mark next to a line of the item: in the savings, only "eventual" or "anual"; in the other items, the type. */
function profileMark(item: BridgeItem, line: BridgeLine): string | null {
  if (item.code === "LINE_SAVINGS") return MARKED_IN_SAVINGS.has(line.effectiveProfile) ? spendingProfileLabel[line.effectiveProfile] : null;
  return spendingProfileLabel[line.effectiveProfile];
}

/** Parts and budget lines of one bridge item, with the lines that contributed the most, up and down (RF-13.6). */
export function BridgeItemDetail({ item, onOpenEvidence }: Props) {
  // Ordering only: the amounts come from the API
  const up = item.lines.filter((l) => l.amount > 0).sort((a, b) => b.amount - a.amount);
  const down = item.lines.filter((l) => l.amount < 0).sort((a, b) => a.amount - b.amount);
  return (
    <section className="block">
      <h2>{item.label}</h2>
      {item.parts.length > 0 && (
        <table className="table compact">
          <tbody>
            {item.parts.map((part) => {
              const target = partTarget(item, part);
              return (
                <tr key={part.code}>
                  <td>{part.label}</td>
                  <td className="number">
                    {target && onOpenEvidence ? (
                      <button className="sourced-value" onClick={() => onOpenEvidence(target)}>
                        {formatDifference(part.amount)}
                      </button>
                    ) : (
                      formatDifference(part.amount)
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      {item.lines.length > 0 && (
        <div className="two-columns">
          <LineList item={item} title="Linhas que mais contribuíram para mais" lines={up.slice(0, TOP)} onOpenEvidence={onOpenEvidence} />
          <LineList item={item} title="Linhas que mais contribuíram para menos" lines={down.slice(0, TOP)} onOpenEvidence={onOpenEvidence} />
        </div>
      )}
      {item.lines.length > TOP && (
        <details className="trail">
          <summary>Todas as {item.lines.length} linhas</summary>
          <LineList item={item} title="" lines={item.lines} onOpenEvidence={onOpenEvidence} />
        </details>
      )}
      {item.funds && item.funds.length > 0 && <FundList funds={item.funds} />}
      {item.parts.length === 0 && item.lines.length === 0 && !item.funds?.length && <p className="muted">Sem partes nem linhas da PO nesta parcela.</p>}
    </section>
  );
}

function LineList({
  item,
  title,
  lines,
  onOpenEvidence,
}: {
  item: BridgeItem;
  title: string;
  lines: BridgeLine[];
  onOpenEvidence: OpenBridgeEvidence | null;
}) {
  return (
    <div>
      {title && <h3>{title}</h3>}
      {lines.length === 0 ? (
        <p className="muted">Nenhuma.</p>
      ) : (
        <table className="table compact">
          <tbody>
            {lines.map((l) => {
              const mark = profileMark(item, l);
              return (
                <tr key={l.lineId}>
                  <td>
                    {l.code} {l.description} {mark && <small className="muted">· {mark}</small>}
                  </td>
                  <td className="number">
                    {onOpenEvidence ? (
                      <button className="sourced-value" onClick={() => onOpenEvidence(lineTarget(l))}>
                        {formatDifference(l.amount)}
                      </button>
                    ) : (
                      formatDifference(l.amount)
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}

/**
 * Funds that make up "Fundos de rateio: entradas − saídas" (Q49), largest amount first, in a collapsed panel.
 * Same numbers as the "Fundos" screen, without the transfers between funds (calculated by the API).
 */
function FundList({ funds }: { funds: BridgeFund[] }) {
  // Ordering only: the amounts come from the API
  const sorted = [...funds].sort((a, b) => b.amount - a.amount);
  return (
    <details className="trail">
      <summary>Os {funds.length} fundos de rateio</summary>
      <table className="table compact">
        <thead>
          <tr>
            <th>Fundo</th>
            <th className="number">Entradas</th>
            <th className="number">Saídas</th>
            <th className="number">Entradas − saídas</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((f) => (
            <tr key={f.fundId}>
              <td>{f.name}</td>
              <td className="number">{formatMoney(f.inflows)}</td>
              <td className="number">{formatMoney(f.outflows)}</td>
              <td className="number">{formatDifference(f.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
