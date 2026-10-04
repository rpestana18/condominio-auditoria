import { useEventosPrevisao } from "../../api/consultasOrcamento";
import type { EventoPrevisao } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora } from "../../formato";

const rotuloEvento: Record<EventoPrevisao["tipo"], string> = {
  CONFIRMADA: "Confirmada",
  SUBSTITUIDA: "Substituída",
  FUNDOS_ALTERADOS: "Fundos alterados",
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
            <strong>{rotuloEvento[e.tipo]}</strong> · {formatarDataHora(e.em)} por {e.usuario}: {e.detalhe}
            {e.justificativa && <span className="discreto"> · justificativa: {e.justificativa}</span>}
          </li>
        ))}
      </ul>
    </details>
  );
}
