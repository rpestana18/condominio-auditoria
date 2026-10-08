import type { Exercicio, MesExercicio } from "../../api/tipos";
import { formatarMes } from "../../formato";

/** Meses de uma situação, já no formato da tela ("09/2026, 10/2026"); vazio quando não há nenhum. */
const listar = (meses: MesExercicio[]) => meses.map((m) => formatarMes(m.mes)).join(", ");

/**
 * Situação do exercício escolhido (RF-11.4 e RF-11.3): meses com e sem fluxo, meses prorrogados,
 * estado do de-para e avisos. Tudo vem do GET /exercicios; a tela só separa a lista para mostrar.
 */
export function ResumoExercicio({ exercicio }: { exercicio: Exercicio }) {
  const doExercicio = exercicio.meses.filter((m) => !m.prorrogado);
  const prorrogados = exercicio.meses.filter((m) => m.prorrogado);
  const comFluxo = listar(doExercicio.filter((m) => m.situacao === "COM_FLUXO"));
  const semFluxo = listar(doExercicio.filter((m) => m.situacao === "SEM_FLUXO"));
  const doisFluxos = listar(doExercicio.filter((m) => m.situacao === "DOIS_FLUXOS"));
  const { depara, prorrogacao } = exercicio;

  return (
    <section className="bloco" aria-label={`Exercício ${exercicio.rotulo}`}>
      <header className="titulo-bloco">
        <h2>
          Exercício {exercicio.rotulo} · {formatarMes(exercicio.inicio)} a {formatarMes(exercicio.fim)}
        </h2>
        {depara && (
          <span className={depara.confirmadas === depara.contas ? "selo ok" : "selo alerta"}>
            De-para: {depara.confirmadas} de {depara.contas} contas confirmadas
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
                <span key={m.mes} className="selo neutro">
                  {formatarMes(m.mes)} · prorrogado
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
              até {formatarMes(prorrogacao.ate)}, por {prorrogacao.por}: “{prorrogacao.justificativa}”
            </dd>
          </>
        )}
      </dl>
      {exercicio.avisos.map((a, i) => (
        <p key={i} className="aviso alerta">
          {a}
        </p>
      ))}
    </section>
  );
}
