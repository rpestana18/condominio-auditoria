import type { ReactNode } from "react";
import type { ExercicioComparado, ValorComparado } from "../../api/tipos";
import { formatarMoedaOuTraco } from "../../formato";
import type { AbridorEvidencia } from "./navegacao";
import { Variacao } from "./Variacao";
import { ValorOuTraco } from "./ValorOuTraco";

/** O que a tabela mostra de cada exercício: o previsto do mês ou os meses comparados (previsto e realizado). */
export type ModoValores = "MONTHLY_PLANNED" | "MESES";

/** Uma linha da tabela: um grupo (1.1 a 1.9) ou uma rubrica, com o valor de cada exercício. */
export interface LinhaComparada {
  chave: string;
  titulo: ReactNode;
  observacao?: string | null;
  valores: ValorComparado[];
}

interface Props {
  exercicios: ExercicioComparado[];
  linhas: LinhaComparada[];
  modo: ModoValores;
  abridor: AbridorEvidencia;
  /** Na visão por linha: mostra as linhas da PO somadas em cada exercício (divisão ou junção de linhas). */
  mostrarLinhasDaPo?: boolean;
}

/** Visões 2 e 3 do RF-11.6: valores de cada exercício lado a lado, com a variação contra o exercício seguinte. */
export function TabelaComparada({ exercicios, linhas, modo, abridor, mostrarLinhasDaPo }: Props) {
  const colunas = modo === "MONTHLY_PLANNED" ? 2 : 3;
  return (
    <div className="rolagem">
      <table className="tabela comparada">
        <thead>
          <tr>
            <th rowSpan={2} scope="col">
              {mostrarLinhasDaPo ? "Rubrica" : "Grupo"}
            </th>
            {exercicios.map((e) => (
              <th key={e.id} colSpan={colunas} scope="colgroup" className="inicio-exercicio">
                {e.label}
              </th>
            ))}
          </tr>
          <tr>
            {exercicios.map((e) =>
              modo === "MONTHLY_PLANNED" ? (
                <CabecalhosValores key={e.id} nomes={["Previsto do mês", "Variação"]} />
              ) : (
                <CabecalhosValores key={e.id} nomes={["Previsto", "Realizado", "Variação do realizado"]} />
              ),
            )}
          </tr>
        </thead>
        <tbody>
          {linhas.map((l) => (
            <tr key={l.chave}>
              <th scope="row">
                {l.titulo}
                {l.observacao && <small className="observacao">{l.observacao}</small>}
              </th>
              {exercicios.map((e) => (
                <CelulasExercicio
                  key={e.id}
                  valor={l.valores.find((v) => v.fiscalYearId === e.id)}
                  modo={modo}
                  exercicioId={e.id}
                  abridor={abridor}
                  mostrarLinhasDaPo={mostrarLinhasDaPo}
                />
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CabecalhosValores({ nomes }: { nomes: string[] }) {
  return (
    <>
      {nomes.map((n, i) => (
        <th key={n} scope="col" className={i === 0 ? "numero inicio-exercicio" : "numero"}>
          {n}
        </th>
      ))}
    </>
  );
}

interface PropsCelulas {
  /** Ausente quando o exercício não tem esse grupo ou rubrica: tudo vira "—". */
  valor: ValorComparado | undefined;
  modo: ModoValores;
  exercicioId: string;
  abridor: AbridorEvidencia;
  mostrarLinhasDaPo?: boolean;
}

function CelulasExercicio({ valor, modo, exercicioId, abridor, mostrarLinhasDaPo }: PropsCelulas) {
  const abrir = abridor(exercicioId, valor?.target);
  const linhasDaPo = mostrarLinhasDaPo && valor && valor.lines.length > 0 && (
    <small className="observacao">
      {valor.lines.map((l, i) => {
        const abrirLinha = abridor(exercicioId, l.target);
        return (
          <span key={l.lineId}>
            {i > 0 && ", "}
            {abrirLinha ? (
              <button type="button" className="botao-link" onClick={abrirLinha} title={l.description}>
                {l.code}
              </button>
            ) : (
              <span title={l.description}>{l.code}</span>
            )}
          </span>
        );
      })}
    </small>
  );

  if (modo === "MONTHLY_PLANNED") {
    return (
      <>
        <td className="numero inicio-exercicio">
          <ValorOuTraco valor={valor?.monthlyPlanned} aoAbrir={abrir} />
          {linhasDaPo}
        </td>
        <td className="numero">
          <Variacao variacao={valor?.monthlyPlannedVariation} />
        </td>
      </>
    );
  }
  return (
    <>
      <td className="numero inicio-exercicio">
        {formatarMoedaOuTraco(valor?.planned)}
        {linhasDaPo}
      </td>
      <td className="numero">
        <ValorOuTraco valor={valor?.actual} aoAbrir={abrir} />
      </td>
      <td className="numero">
        <Variacao variacao={valor?.actualVariation} />
      </td>
    </>
  );
}
