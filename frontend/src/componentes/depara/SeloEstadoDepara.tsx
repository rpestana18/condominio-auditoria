import type { EstadoDepara } from "../../api/tipos";
import { rotuloEstadoDepara } from "../previsto/rotulos";

const classes: Record<EstadoDepara, string> = { CONFIRMED: "ok", SUGGESTED: "alerta", REJECTED: "neutro" };

export function SeloEstadoDepara({ estado }: { estado: EstadoDepara | null | undefined }) {
  if (!estado) return <span className="selo neutro">Sem de-para</span>;
  return <span className={`selo ${classes[estado]}`}>{rotuloEstadoDepara[estado]}</span>;
}
