import type { EstadoRubrica } from "../../api/tipos";
import { rotuloEstadoRubrica } from "../previsto/rotulos";

const classes: Record<EstadoRubrica, string> = { CONFIRMADO: "ok", SUGERIDO: "alerta", RECUSADO: "neutro" };

export function SeloEstadoRubrica({ estado }: { estado: EstadoRubrica | null | undefined }) {
  if (!estado) return <span className="selo neutro">Sem rubrica</span>;
  return <span className={`selo ${classes[estado]}`}>{rotuloEstadoRubrica[estado]}</span>;
}
