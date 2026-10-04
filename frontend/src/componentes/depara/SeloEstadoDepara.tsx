import type { EstadoDepara } from "../../api/tipos";
import { rotuloEstadoDepara } from "../previsto/rotulos";

const classes: Record<EstadoDepara, string> = { CONFIRMADO: "ok", SUGERIDO: "alerta", RECUSADO: "neutro" };

export function SeloEstadoDepara({ estado }: { estado: EstadoDepara | null | undefined }) {
  if (!estado) return <span className="selo neutro">Sem de-para</span>;
  return <span className={`selo ${classes[estado]}`}>{rotuloEstadoDepara[estado]}</span>;
}
