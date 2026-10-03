import { useState } from "react";
import { useArquivos, useCategorias } from "../api/consultas";
import type { Categoria } from "../api/tipos";
import { DetalheArquivo } from "../componentes/DetalheArquivo";
import { EnvioArquivo } from "../componentes/EnvioArquivo";
import { StatusArquivo } from "../componentes/StatusArquivo";
import { useSessao } from "../contexto";
import { formatarDataHora, formatarPeriodo } from "../formato";

/** Arquivos separados por categoria, do mais recente para o mais antigo. */
export function Arquivos() {
  const { condominioId, pode } = useSessao();
  const { data: categorias = [] } = useCategorias();
  const [categoria, setCategoria] = useState<Categoria | undefined>();
  const [selecionado, setSelecionado] = useState<string | null>(null);
  const { data: arquivos = [], isLoading } = useArquivos(condominioId, categoria);

  return (
    <div className={selecionado ? "com-detalhe" : undefined}>
      <div>
        <header className="titulo-pagina">
          <h1>Arquivos</h1>
        </header>
        {pode("GESTOR", "ADMIN") && <EnvioArquivo categoriaInicial={categoria} />}

        <div className="abas" role="tablist">
          <button role="tab" aria-selected={!categoria} onClick={() => setCategoria(undefined)}>
            Todas
          </button>
          {categorias.map((c) => (
            <button key={c.codigo} role="tab" aria-selected={categoria === c.codigo} onClick={() => setCategoria(c.codigo)}>
              {c.rotulo}
            </button>
          ))}
        </div>

        {isLoading ? (
          <p className="aviso">Carregando…</p>
        ) : arquivos.length === 0 ? (
          <p className="aviso">Nenhum arquivo nesta categoria ainda.</p>
        ) : (
          <table className="tabela clicavel">
            <thead>
              <tr>
                <th>Arquivo</th>
                {!categoria && <th>Categoria</th>}
                <th>Período</th>
                <th>Enviado em</th>
                <th>Situação</th>
              </tr>
            </thead>
            <tbody>
              {arquivos.map((a) => (
                <tr key={a.id} onClick={() => setSelecionado(a.id)} className={a.id === selecionado ? "selecionado" : undefined}>
                  <td>{a.nome}</td>
                  {!categoria && <td>{a.categoriaRotulo}</td>}
                  <td>{formatarPeriodo(a.periodoInicio, a.periodoFim)}</td>
                  <td>
                    {formatarDataHora(a.enviadoEm)} <span className="discreto">por {a.enviadoPor}</span>
                  </td>
                  <td>
                    <StatusArquivo status={a.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {selecionado && <DetalheArquivo id={selecionado} aoFechar={() => setSelecionado(null)} />}
    </div>
  );
}
