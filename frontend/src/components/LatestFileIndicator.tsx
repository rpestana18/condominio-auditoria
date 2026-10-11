import { Link } from "react-router";
import { useLatestFile } from "../api/queries";
import { useSession } from "../context";
import { formatDateTime } from "../format";

/** Discreet indicator, fixed in the corner, on every screen. */
export function LatestFileIndicator() {
  const { condominiumId } = useSession();
  const { data: latest } = useLatestFile(condominiumId);
  if (!latest) return null;
  return (
    <Link to="/arquivos" className="latest-file" title={`Enviado por ${latest.uploadedBy}`}>
      Último arquivo: {latest.name} · {latest.categoryLabel} · {formatDateTime(latest.uploadedAt)}
    </Link>
  );
}
