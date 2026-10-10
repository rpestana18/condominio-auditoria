import { useEventosPrevisao } from "../../api/consultasOrcamento";
import type { EventoPrevisao } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora } from "../../formato";

const rotuloEvento: Record<EventoPrevisao["type"], string> = {
  CONFIRMED: "Confirmada",
  SUPERSEDED: "Substituída",
  FUNDS_CHANGED: "Fundos alterados",
  EXTENDED: "Prorrogada",
  EXTENSION_UNDONE: "Prorrogação desfeita",
  EXTENSION_SHORTENED: "Prorrogação encurtada",
};

/** Trilha da PO (só de inserção): confirmação, substituição e mudanças na ligação dos fundos. */
export function EventosPo({ poId }: { poId: string }) {
  const { condominioId } = useSessao();
  const { data: eventos = [] } = useEventosPrevisao(condominioId, poId);
  if (eventos.length === 0) return null;
  return (
    <details className="bloco">
      <summary>Histórico da PO ({eventos.length})</summary>
      <ul className="lista-simples">
        {eventos.map((e, i) => (
          <li key={i}>
            <strong>{rotuloEvento[e.type]}</strong> · {formatarDataHora(e.at)} por {e.username}: {e.detail}
            {e.justification && <span className="discreto"> · justificativa: {e.justification}</span>}
          </li>
        ))}
      </ul>
    </details>
  );
}
