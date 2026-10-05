import { useEventosModulo, usePeriodosModulo } from "../../api/consultas";
import type { EventoModulo, PeriodoAtivo } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora } from "../../formato";

/** Trilha de ativação e períodos ativos de um módulo (RF-10.6). Só ADMIN; o backend recusa os demais. */
export function TrilhaModulo({ codigo }: { codigo: string }) {
  return (
    <div className="duas-colunas trilha">
      <Eventos codigo={codigo} />
      <Periodos codigo={codigo} />
    </div>
  );
}

const estado = (ligado: boolean) => (ligado ? "Ligado" : "Desligado");

function Eventos({ codigo }: { codigo: string }) {
  const { condominioId } = useSessao();
  const { data: eventos = [], isLoading, error } = useEventosModulo(condominioId, codigo);
  // A API manda do mais antigo para o mais recente; na tela, o mais recente vem primeiro
  const recentesPrimeiro: EventoModulo[] = [...eventos].reverse();

  return (
    <section>
      <h3>Trilha de ativação</h3>
      {isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : error ? (
        <p className="aviso erro">{error.message}</p>
      ) : recentesPrimeiro.length === 0 ? (
        <p className="aviso">Nenhuma alteração registrada. Vale o estado padrão.</p>
      ) : (
        <table className="tabela compacta">
          <thead>
            <tr>
              <th>Quando</th>
              <th>Mudança</th>
              <th>Quem</th>
              <th>Motivo</th>
            </tr>
          </thead>
          <tbody>
            {recentesPrimeiro.map((e) => (
              <tr key={e.id}>
                <td>{formatarDataHora(e.quando)}</td>
                <td>
                  {estado(e.ligadoAntes)} → <strong>{estado(e.ligadoDepois)}</strong>
                </td>
                <td>{e.usuario}</td>
                <td>{e.motivo || <span className="discreto">—</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Periodos({ codigo }: { codigo: string }) {
  const { condominioId } = useSessao();
  const { data: periodos = [], isLoading, error } = usePeriodosModulo(condominioId, codigo);

  return (
    <section>
      <h3>Períodos ativos</h3>
      {isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : error ? (
        <p className="aviso erro">{error.message}</p>
      ) : periodos.length === 0 ? (
        <p className="aviso">O módulo nunca esteve ligado neste condomínio.</p>
      ) : (
        <table className="tabela compacta">
          <thead>
            <tr>
              <th>Início</th>
              <th>Fim</th>
              <th>Ligado por</th>
              <th>Desligado por</th>
            </tr>
          </thead>
          <tbody>
            {periodos.map((p, i) => (
              <LinhaPeriodo key={`${p.inicio ?? "padrao"}-${i}`} periodo={p} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function LinhaPeriodo({ periodo: p }: { periodo: PeriodoAtivo }) {
  return (
    <tr>
      <td>{p.inicio ? formatarDataHora(p.inicio) : <span className="discreto">Ligado por padrão</span>}</td>
      <td>{p.fim ? formatarDataHora(p.fim) : <strong className="positivo">Ainda ligado</strong>}</td>
      <td>
        <QuemEMotivo quem={p.ligadoPor} motivo={p.motivoLigar} />
      </td>
      <td>
        <QuemEMotivo quem={p.desligadoPor} motivo={p.motivoDesligar} />
      </td>
    </tr>
  );
}

function QuemEMotivo({ quem, motivo }: { quem?: string | null; motivo?: string | null }) {
  if (!quem) return <span className="discreto">—</span>;
  return (
    <>
      {quem}
      {motivo && <small className="discreto bloco-texto">{motivo}</small>}
    </>
  );
}
