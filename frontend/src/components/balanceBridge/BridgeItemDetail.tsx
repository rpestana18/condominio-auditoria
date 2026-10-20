import type { BridgeItem, BridgeLine } from "../../api/types";
import { formatDifference } from "../../format";
import { spendingProfileLabel } from "../budgetVsActual/labels";
import { lineTarget, partTarget, type OpenBridgeEvidence } from "./bridgeEvidence";

interface Props {
  item: BridgeItem;
  /** Null when the entries cannot be opened for this view (e.g. another fund, or a range that is not one month). */
  onOpenEvidence: OpenBridgeEvidence | null;
}

/** How many lines to show on each side ("para mais" and "para menos"); the rest stays in the full list. */
const TOP = 10;

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
          <LineList title="Linhas que mais contribuíram para mais" lines={up.slice(0, TOP)} onOpenEvidence={onOpenEvidence} />
          <LineList title="Linhas que mais contribuíram para menos" lines={down.slice(0, TOP)} onOpenEvidence={onOpenEvidence} />
        </div>
      )}
      {item.lines.length > TOP && (
        <details className="trail">
          <summary>Todas as {item.lines.length} linhas</summary>
          <LineList title="" lines={item.lines} onOpenEvidence={onOpenEvidence} />
        </details>
      )}
      {item.parts.length === 0 && item.lines.length === 0 && <p className="muted">Sem partes nem linhas da PO nesta parcela.</p>}
    </section>
  );
}

function LineList({ title, lines, onOpenEvidence }: { title: string; lines: BridgeLine[]; onOpenEvidence: OpenBridgeEvidence | null }) {
  return (
    <div>
      {title && <h3>{title}</h3>}
      {lines.length === 0 ? (
        <p className="muted">Nenhuma.</p>
      ) : (
        <table className="table compact">
          <tbody>
            {lines.map((l) => (
              <tr key={l.lineId}>
                <td>
                  {l.code} {l.description} <small className="muted">· {spendingProfileLabel[l.effectiveProfile]}</small>
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
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
