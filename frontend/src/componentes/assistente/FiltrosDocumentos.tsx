import { useArquivos, useCategorias } from "../../api/consultas";
import type { Categoria, FiltrosDocumentos as Filtros } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarPeriodo } from "../../formato";
import { useConversa } from "./conversa";

/** Liga ou desliga um item numa lista (marcar/desmarcar uma caixa). */
function alternar<T>(lista: T[] | undefined, item: T): T[] {
  const atual = lista ?? [];
  return atual.includes(item) ? atual.filter((i) => i !== item) : [...atual, item];
}

/** Resumo do que está filtrado, para o título fechado: "2 categorias · 01/01/2025 a 31/12/2025". */
function resumo(filtros: Filtros): string {
  const partes: string[] = [];
  const categorias = filtros.categorias?.length ?? 0;
  const documentos = filtros.arquivoIds?.length ?? 0;
  if (categorias) partes.push(categorias === 1 ? "1 categoria" : `${categorias} categorias`);
  if (filtros.dataInicio || filtros.dataFim) {
    partes.push(`${formatarData(filtros.dataInicio) || "…"} a ${formatarData(filtros.dataFim) || "…"}`);
  }
  if (documentos) partes.push(documentos === 1 ? "1 documento" : `${documentos} documentos`);
  return partes.length ? partes.join(" · ") : "todos os documentos";
}

/**
 * Filtros opcionais (RF-04.10): categorias, período e documentos. Valem para o chat e para a busca.
 * Só monta o pedido; quem aplica é o backend.
 */
export function FiltrosDocumentos() {
  const { condominioId } = useSessao();
  const { filtros, alterarFiltros } = useConversa();
  const { data: categorias = [] } = useCategorias();
  const { data: arquivos = [] } = useArquivos(condominioId);
  const ativo = Boolean(filtros.categorias?.length || filtros.arquivoIds?.length || filtros.dataInicio || filtros.dataFim);

  return (
    <details className="bloco filtros-documentos">
      <summary>
        Filtros: <span className="discreto">{resumo(filtros)}</span>
      </summary>

      <div className="filtros-grade">
        <fieldset>
          <legend>Categorias</legend>
          <div className="lista-opcoes">
            {categorias.map((c) => (
              <label key={c.codigo}>
                <input
                  type="checkbox"
                  checked={filtros.categorias?.includes(c.codigo) ?? false}
                  onChange={() => alterarFiltros({ ...filtros, categorias: alternar<Categoria>(filtros.categorias, c.codigo) })}
                />
                {c.rotulo}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend>Período (competência)</legend>
          <label className="campo">
            De
            <input
              type="date"
              value={filtros.dataInicio ?? ""}
              onChange={(e) => alterarFiltros({ ...filtros, dataInicio: e.target.value || null })}
            />
          </label>
          <label className="campo">
            Até
            <input
              type="date"
              value={filtros.dataFim ?? ""}
              min={filtros.dataInicio ?? undefined}
              onChange={(e) => alterarFiltros({ ...filtros, dataFim: e.target.value || null })}
            />
          </label>
          <p className="discreto">Convenção, regimento e contratos sem competência podem entrar mesmo com período.</p>
        </fieldset>

        <fieldset>
          <legend>Documentos</legend>
          {arquivos.length === 0 ? (
            <p className="discreto">Nenhum arquivo enviado ainda.</p>
          ) : (
            <div className="lista-opcoes rolavel">
              {arquivos.map((a) => (
                <label key={a.id} title={a.nome}>
                  <input
                    type="checkbox"
                    checked={filtros.arquivoIds?.includes(a.id) ?? false}
                    onChange={() => alterarFiltros({ ...filtros, arquivoIds: alternar(filtros.arquivoIds, a.id) })}
                  />
                  <span className="nome-documento">{a.nome}</span>
                  <span className="discreto">
                    {a.categoriaRotulo}
                    {a.periodoInicio && ` · ${formatarPeriodo(a.periodoInicio, a.periodoFim)}`}
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>
      </div>

      {ativo && (
        <button type="button" className="botao-link" onClick={() => alterarFiltros({})}>
          Limpar filtros
        </button>
      )}
    </details>
  );
}
