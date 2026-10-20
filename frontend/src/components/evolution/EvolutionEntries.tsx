import { openFile } from "../../api/client";
import type { MonthlySeries } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatMoney, formatMonth } from "../../format";
import { pointText } from "./formatting";

interface Props {
  title: string;
  month: string;
  series: MonthlySeries[];
  onClose: () => void;
}

/**
 * Entries that make up the amounts of a month (RF-12.5): date, memo, account, fund, month of reference, file and page.
 * Only facts, no text about the cause of a difference.
 */
export function EvolutionEntries({ title, month, series, onClose }: Props) {
  const { condominiumId } = useSession();
  const openOnPage = (fileId: string, page: number) =>
    void openFile(`/condominiums/${condominiumId}/files/${fileId}/content`, page);

  return (
    <section className="block evolution-entries" aria-label={`Lançamentos de ${formatMonth(month)}`}>
      <header className="evolution-entries-header">
        <h3>
          {title} · {formatMonth(month)}
        </h3>
        <button className="link-button" onClick={onClose} aria-label="Fechar">
          ✕
        </button>
      </header>
      {series.map((s) => {
        const point = s.points.find((p) => p.month === month);
        if (!point) return null;
        return (
          <div key={s.key}>
            <h4>
              {s.label}: {pointText(point, s)}
            </h4>
            {point.entries.length > 0 && (
              <ul className="evidence-list">
                {point.entries.map((e) => (
                  <li key={e.entryId}>
                    <div className="evidence-header">
                      <strong>{formatMoney(e.amount)}</strong>
                      <span className="muted">{formatDate(e.date)}</span>
                    </div>
                    <span>{e.memo}</span>
                    <span className="muted">
                      {e.account && `Conta ${e.account}${e.accountName ? ` ${e.accountName}` : ""}`}
                      {e.fund && ` · ${e.fund}`}
                      {e.referenceMonth && ` · referência ${formatMonth(e.referenceMonth)}`}
                    </span>
                    <span className="muted">
                      <button className="link-button" onClick={() => openOnPage(e.fileId, e.page)}>
                        {e.fileName ?? "Fluxo"}, pág. {e.page}
                      </button>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
    </section>
  );
}
