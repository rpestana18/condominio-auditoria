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
    ordem === "PO" ? linhas : [...linhas].sort((a, b) => b.diferenca - a.diferenca);

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
            <tbody key={g.linhaId}>
              <tr className="linha-grupo" onClick={() => alternar(g.linhaId)}>
                <td>
                  <span aria-hidden>{fechados.has(g.linhaId) ? "▸" : "▾"}</span> {g.codigo}
                </td>
                <td colSpan={2}>{g.descricao}</td>
                <td className="numero">{formatarMoeda(g.previsto)}</td>
                <td className="numero">
                  <ValorComFonte
                    valor={g.realizado}
                    aoAbrir={() => aoAbrirEvidencia({ alvo: `grupo:${g.linhaId}`, titulo: `${g.codigo} ${g.descricao}` })}
                  />
                </td>
                <td className={`numero ${classeDiferenca(g.diferenca)}`}>{formatarDiferenca(g.diferenca)}</td>
                <td className="numero">{formatarPercentual(g.execucao)}</td>
              </tr>
              {!fechados.has(g.linhaId) &&
                ordenar(g.linhas).map((l) => <LinhaTabela key={l.linhaId} linha={l} aoAbrirEvidencia={aoAbrirEvidencia} />)}
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
      alvo: `linha:${linha.linhaId}`,
      titulo: `${linha.codigo} ${linha.descricao}`,
      linhaPo: { pagina: linha.pagina, observacoes: linha.observacoes },
    });
  return (
    <tr>
      <td>{linha.codigo}</td>
      <td>
        {linha.descricao}
        {linha.marca && <span className="selo neutro">{rotuloMarca[linha.marca]}</span>}
        {linha.observacoes && <small className="observacao">Obs. da PO: {linha.observacoes}</small>}
      </td>
      <td className="discreto">{linha.contasFluxo.join(", ") || "—"}</td>
      <td className="numero">{formatarMoeda(linha.previsto)}</td>
      <td className="numero">
        <ValorComFonte valor={linha.realizado} aoAbrir={abrir} />
        {linha.lancamentos > 0 && <small className="discreto"> ({linha.lancamentos})</small>}
      </td>
      <td className={`numero ${classeDiferenca(linha.diferenca)}`}>{formatarDiferenca(linha.diferenca)}</td>
      <td className="numero">{formatarPercentual(linha.execucao)}</td>
    </tr>
  );
}
