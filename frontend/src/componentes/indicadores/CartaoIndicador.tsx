import type { ReactNode } from "react";
import { formatarDataHora } from "../../formato";

interface Props {
  /** Número do gráfico no RF-11.11 (1 a 7). */
  numero: number;
  titulo: string;
  /** Ex.: "09/2026 a 08/2027". */
  periodo: string;
  /** "R$", "%" ou a combinação, quando o cartão tem mais de uma escala (gráfico 7). */
  unidade: string;
  /** Envio mais recente dos fluxos usados (ISO), do campo `dadosDe` da API. */
  dadosDe: string | null | undefined;
  /** Uma frase de uso (ex.: "Clique numa barra para abrir o mês"). Nunca explica causa ou tendência (RF-11.13). */
  dica?: string;
  /** O gráfico. */
  children: ReactNode;
  /** A tabela alternativa (ou mais de uma), com os mesmos valores do gráfico. */
  tabela: ReactNode;
}

/** Moldura comum dos 7 gráficos (RF-11.10): título, período, unidade, data dos dados e "Ver tabela". */
export function CartaoIndicador({ numero, titulo, periodo, unidade, dadosDe, dica, children, tabela }: Props) {
  const idTitulo = `indicador-${numero}`;
  return (
    <section className="bloco grafico indicador" aria-labelledby={idTitulo}>
      <h2 id={idTitulo}>
        {numero}. {titulo}
      </h2>
      <p className="discreto meta-indicador">
        <span>Período: {periodo}</span>
        <span>Unidade: {unidade}</span>
        <span>Dados de: {dadosDe ? formatarDataHora(dadosDe) : "sem fluxo carregado"}</span>
      </p>
      {children}
      {dica && <p className="discreto">{dica}</p>}
      <details className="tabela-alternativa">
        <summary>Ver tabela</summary>
        {tabela}
      </details>
    </section>
  );
}

/** Nota neutra no lugar de um gráfico cuja série veio nula para o filtro escolhido. */
export function NaoSeAplica({ titulo, motivo }: { titulo: string; motivo: string }) {
  return (
    <section className="bloco indicador-nao-se-aplica">
      <h2>{titulo}</h2>
      <p className="discreto">{motivo}</p>
    </section>
  );
}
