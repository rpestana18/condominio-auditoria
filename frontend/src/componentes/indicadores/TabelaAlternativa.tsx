import type { TooltipContentProps } from "recharts";

/**
 * Modelo da tabela alternativa de um gráfico (RF-11.10). O mesmo modelo alimenta a tabela e a dica (tooltip)
 * do gráfico, então os dois mostram exatamente os mesmos textos. Uma linha por categoria do eixo (mês, linha
 * da PO ou grupo), na mesma ordem dos dados do gráfico.
 */
export interface ModeloTabela {
  /** Legenda da tabela (caption), para leitores de tela. */
  legenda: string;
  /** Título da primeira coluna (ex.: "Mês"). */
  colunaRotulo: string;
  colunas: string[];
  linhas: LinhaTabela[];
}

export interface LinhaTabela {
  chave: string;
  /** Primeira coluna (ex.: "09/2026" ou "1.3.10 Vigia e Portaria"). */
  rotulo: string;
  celulas: Celula[];
}

export interface Celula {
  texto: string;
  /** Com destino: vira link para o previsto × realizado com a evidência (RF-11.12). */
  aoAbrir?: () => void;
  /** Só o excesso acima do limite da regra dos 20% (RF-11.13), quando a API marca `acimaDoLimite`. */
  alerta?: boolean;
}

/** A tabela, dentro de um "Ver tabela" que a pessoa abre quando quer. */
export function TabelaAlternativa({ modelo }: { modelo: ModeloTabela }) {
  return (
    <div className="rolagem">
      <table className="tabela compacta">
        <caption className="discreto">{modelo.legenda}</caption>
        <thead>
          <tr>
            <th scope="col">{modelo.colunaRotulo}</th>
            {modelo.colunas.map((c) => (
              <th key={c} scope="col" className="numero">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {modelo.linhas.map((linha) => (
            <tr key={linha.chave}>
              <th scope="row">{linha.rotulo}</th>
              {linha.celulas.map((celula, i) => (
                <td key={i} className="numero">
                  <TextoCelula celula={celula} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TextoCelula({ celula }: { celula: Celula }) {
  const classe = celula.alerta ? "texto-critico" : undefined;
  if (!celula.aoAbrir) return <span className={classe}>{celula.texto}</span>;
  return (
    <button type="button" className={`valor-fonte ${classe ?? ""}`} onClick={celula.aoAbrir} title="Abrir no previsto × realizado, com os lançamentos">
      {celula.texto}
    </button>
  );
}

/**
 * Dica do gráfico: a linha da tabela da categoria sob o mouse. Use em `<Tooltip content={dicaDaTabela(modelo)} />`.
 * Os dados do gráfico ficam na mesma ordem das linhas do modelo e levam o campo `indice` (posição da linha).
 */
export function dicaDaTabela(modelo: ModeloTabela) {
  return function Dica({ active, activeIndex, payload }: TooltipContentProps) {
    // A posição da categoria sob o mouse; mês sem fluxo pode vir sem itens no payload, por isso o activeIndex primeiro
    const doPayload = (payload?.[0]?.payload as { indice?: number } | undefined)?.indice;
    const indice = activeIndex !== null && activeIndex !== undefined && activeIndex !== "" ? Number(activeIndex) : doPayload;
    const linha = indice === undefined || Number.isNaN(indice) ? undefined : modelo.linhas[indice];
    if (!active || !linha) return null;
    return (
      <div className="dica-grafico">
        <strong>{linha.rotulo}</strong>
        <dl>
          {modelo.colunas.map((coluna, i) => (
            <div key={coluna}>
              <dt>{coluna}</dt>
              <dd className={linha.celulas[i]?.alerta ? "texto-critico" : undefined}>{linha.celulas[i]?.texto}</dd>
            </div>
          ))}
        </dl>
      </div>
    );
  };
}
