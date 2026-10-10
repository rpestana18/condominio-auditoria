import { useState } from "react";
import type { GrupoPrevistoRealizado, LinhaPrevistoRealizado } from "../../api/tipos";
import { classeDiferenca, formatarDiferenca, formatarMoeda, formatarPercentual } from "../../formato";
import type { AbrirEvidencia } from "./evidencia";
import { rotuloMarca } from "./rotulos";
import { ValorComFonte } from "./ValorComFonte";

type Ordem = "PO" | "DIFERENCA";

interface Props {
  grupos: GrupoPrevistoRealizado[];
  aoAbrirEvidencia: AbrirEvidencia;
}

/** Tabela por grupo, com subtotais (RF-03.1.13). Cada realizado, de linha ou de grupo, leva aos lançamentos. */
export function TabelaPrevisto({ grupos, aoAbrirEvidencia }: Props) {
  const [ordem, setOrdem] = useState<Ordem>("PO");
  const [fechados, setFechados] = useState<Set<string>>(new Set());

  const alternar = (id: string) =>
    setFechados((atual) => {
      const novo = new Set(atual);
      if (novo.has(id)) novo.delete(id);
      else novo.add(id);
      return novo;
    });

  // Só muda a ordem de exibição; os valores continuam os da API
  const ordenar = (linhas: LinhaPrevistoRealizado[]) =>
    ordem === "PO" ? linhas : [...linhas].sort((a, b) => b.difference - a.difference);

  return (
    <section className="bloco">
      <header className="titulo-bloco">
        <h2>Linhas da PO</h2>
        <label className="discreto">
          Ordenar por{" "}
          <select value={ordem} onChange={(e) => setOrdem(e.target.value as Ordem)}>
            <option value="PO">ordem da PO</option>
            <option value="DIFERENCA">maior diferença</option>
          </select>
        </label>
      </header>
      <div className="rolagem">
        <table className="tabela previsto">
          <thead>
            <tr>
              <th>Código</th>
              <th>Descrição</th>
              <th>Contas do fluxo</th>
              <th className="numero">Previsto</th>
              <th className="numero">Realizado</th>
              <th className="numero">Diferença</th>
              <th className="numero">Execução</th>
            </tr>
          </thead>
          {grupos.map((g) => (
            <tbody key={g.lineId}>
              <tr className="linha-grupo" onClick={() => alternar(g.lineId)}>
                <td>
                  <span aria-hidden>{fechados.has(g.lineId) ? "▸" : "▾"}</span> {g.code}
                </td>
                <td colSpan={2}>{g.description}</td>
                <td className="numero">{formatarMoeda(g.planned)}</td>
                <td className="numero">
                  <ValorComFonte
                    valor={g.actual}
                    aoAbrir={() => aoAbrirEvidencia({ alvo: `group:${g.lineId}`, titulo: `${g.code} ${g.description}` })}
                  />
                </td>
                <td className={`numero ${classeDiferenca(g.difference)}`}>{formatarDiferenca(g.difference)}</td>
                <td className="numero">{formatarPercentual(g.execution)}</td>
              </tr>
              {!fechados.has(g.lineId) &&
                ordenar(g.lines).map((l) => <LinhaTabela key={l.lineId} linha={l} aoAbrirEvidencia={aoAbrirEvidencia} />)}
            </tbody>
          ))}
        </table>
      </div>
    </section>
  );
}

function LinhaTabela({ linha, aoAbrirEvidencia }: { linha: LinhaPrevistoRealizado; aoAbrirEvidencia: AbrirEvidencia }) {
  const abrir = () =>
    aoAbrirEvidencia({
      alvo: `line:${linha.lineId}`,
      titulo: `${linha.code} ${linha.description}`,
      linhaPo: { pagina: linha.page, observacoes: linha.notes },
    });
  return (
    <tr>
      <td>{linha.code}</td>
      <td>
        {linha.description}
        {linha.mark && <span className="selo neutro">{rotuloMarca[linha.mark]}</span>}
        {linha.notes && <small className="observacao">Obs. da PO: {linha.notes}</small>}
      </td>
      <td className="discreto">{linha.cashFlowAccounts.join(", ") || "—"}</td>
      <td className="numero">{formatarMoeda(linha.planned)}</td>
      <td className="numero">
        <ValorComFonte valor={linha.actual} aoAbrir={abrir} />
        {linha.entries > 0 && <small className="discreto"> ({linha.entries})</small>}
      </td>
      <td className={`numero ${classeDiferenca(linha.difference)}`}>{formatarDiferenca(linha.difference)}</td>
      <td className="numero">{formatarPercentual(linha.execution)}</td>
    </tr>
  );
}
