import { useState } from "react";
import { downloadFile } from "../../api/client";
import { exportPath, type ExportFormat, type Period } from "../../api/budgetQueries";
import { useSession } from "../../context";

interface Props {
  period: Period;
  budgetId: string | null;
  /** Same fund filter as the screen; without it, all funds. */
  fundId: string | null;
}

/**
 * PDF and Excel export of the chosen view (RF-03.1.14). Only happens when the person clicks:
 * the file is generated in the backend from the same calculation as the screen.
 */
export function ExportButtons({ period, budgetId, fundId }: Props) {
  const { condominiumId } = useSession();
  const [generating, setGenerating] = useState<ExportFormat | null>(null);
  const [error, setError] = useState<string | null>(null);

  const runExport = async (format: ExportFormat) => {
    setGenerating(format);
    setError(null);
    try {
      await downloadFile(exportPath(condominiumId, format, period, budgetId, fundId), `previsto-realizado-${period === "cumulative" ? "acumulado" : period}.${format}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível exportar");
    } finally {
      setGenerating(null);
    }
  };

  return (
    <div className="export-actions">
      <button className="button secondary" disabled={generating !== null} onClick={() => void runExport("pdf")}>
        {generating === "pdf" ? "Gerando PDF…" : "Exportar PDF"}
      </button>
      <button className="button secondary" disabled={generating !== null} onClick={() => void runExport("xlsx")}>
        {generating === "xlsx" ? "Gerando Excel…" : "Exportar Excel"}
      </button>
      {error && <span className="notice error">{error}</span>}
    </div>
  );
}
