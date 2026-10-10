import type { Exercicio, MesExercicio } from "../../api/tipos";
import { formatarMes } from "../../formato";

/** Meses de uma situação, já no formato da tela ("09/2026, 10/2026"); vazio quando não há nenhum. */
const listar = (meses: MesExercicio[]) => meses.map((m) => formatarMes(m.month)).join(", ");

/**
 * Situação do exercício escolhido (RF-11.4 e RF-11.3): meses com e sem fluxo, meses prorrogados,
 * estado do de-para e avisos. Tudo vem do GET /exercicios; a tela só separa a lista para mostrar.
 */
export function ResumoExercicio({ exercicio }: { exercicio: Exercicio }) {
  const doExercicio = exercicio.months.filter((m) => !m.extended);
  const prorrogados = exercicio.months.filter((m) => m.extended);
  const comFluxo = listar(doExercicio.filter((m) => m.status === "WITH_CASH_FLOW"));
  const semFluxo = listar(doExercicio.filter((m) => m.status === "NO_CASH_FLOW"));
  const doisFluxos = listar(doExercicio.filter((m) => m.status === "TWO_CASH_FLOWS"));
  const { mapping: depara, extension: prorrogacao } = exercicio;

  return (
    <section className="bloco" aria-label={`Exercício ${exercicio.label}`}>
      <header className="titulo-bloco">
        <h2>
          Exercício {exercicio.label} · {formatarMes(exercicio.start)} a {formatarMes(exercicio.end)}
        </h2>
        {depara && (
          <span className={depara.confirmed === depara.accounts ? "selo ok" : "selo alerta"}>
            De-para: {depara.confirmed} de {depara.accounts} contas confirmadas
          </span>
        )}
      </header>
      <dl className="lista-numeros">
        <dt>Com fluxo</dt>
        <dd>{comFluxo || "nenhum mês"}</dd>
        <dt>Sem fluxo carregado</dt>
        <dd>{semFluxo || "nenhum mês"}</dd>
        {doisFluxos && (
          <>
            <dt>Com dois fluxos</dt>
            <dd>{doisFluxos}</dd>
          </>
        )}
        {prorrogados.length > 0 && (
          <>
            <dt>Prorrogados</dt>
            <dd>
              {prorrogados.map((m) => (
                <span key={m.month} className="selo neutro">
                  {formatarMes(m.month)} · prorrogado
                </span>
              ))}{" "}
              <span className="discreto">fora do acumulado do exercício</span>
            </dd>
          </>
        )}
        {prorrogacao && (
          <>
            <dt>Prorrogação</dt>
            <dd>
              até {formatarMes(prorrogacao.until)}, por {prorrogacao.by}: “{prorrogacao.justification}”
            </dd>
          </>
        )}
      </dl>
      {exercicio.warnings.map((a, i) => (
        <p key={i} className="aviso alerta">
          {a}
        </p>
      ))}
    </section>
  );
}
