import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { baixarArquivo } from "../../api/cliente";
import { caminhoUso, periodoPreenchido, useUso } from "../../api/consultas";
import type { FuncaoUso, TotalUso } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarDolarTexto, formatarInteiro, formatarMesAbreviado, hojeIso } from "../../formato";

const rotulosFuncao: Record<FuncaoUso, string> = {
  busca_documentos: "Busca nos documentos (tela)",
  chamada_mcp: "Busca pelo MCP",
  indexacao: "Indexação de arquivos",
  embeddings: "Embeddings",
  pergunta: "Perguntas ao assistente",
};

/** Uso dos módulos no período, por função e por mês (RF-09.7), e exportação em planilha (RF-10.6). */
export function UsoModulos({ nomesModulos }: { nomesModulos: Record<string, string> }) {
  const { condominioId } = useSessao();
  const hoje = hojeIso();
  const [inicio, setInicio] = useState(`${hoje.slice(0, 4)}-01-01`);
  const [fim, setFim] = useState(hoje);
  const { data: uso, isLoading, error } = useUso(condominioId, inicio, fim);
  const periodoOk = periodoPreenchido(inicio, fim);

  // Exportar é sempre um clique explícito; o nome do arquivo vem do servidor (Content-Disposition)
  const exportar = useMutation({
    mutationFn: () =>
      baixarArquivo(caminhoUso(condominioId, inicio, fim, true), `uso-modulos-${inicio}-a-${fim}.xlsx`),
  });

  return (
    <section className="bloco" aria-labelledby="titulo-uso">
      <header className="cabecalho-bloco">
        <h2 id="titulo-uso">Uso no período</h2>
        <div className="filtros">
          <label className="campo">
            De
            <input type="date" value={inicio} max={fim || undefined} onChange={(e) => setInicio(e.target.value)} />
          </label>
          <label className="campo">
            Até
            <input type="date" value={fim} min={inicio || undefined} onChange={(e) => setFim(e.target.value)} />
          </label>
          <button
            className="botao secundario"
            disabled={!periodoOk || exportar.isPending}
            onClick={() => exportar.mutate()}
          >
            {exportar.isPending ? "Exportando…" : "Exportar planilha"}
          </button>
        </div>
      </header>
      <p className="discreto">
        Quantidades registradas pelo sistema. O custo é uma estimativa em dólar (tokens × preço do catálogo de IA),
        calculada pelo servidor; não é valor de cobrança. A planilha traz também os períodos ativos.
      </p>
      {exportar.isError && (
        <p className="aviso erro" role="alert">
          Não foi possível exportar: {exportar.error.message}
        </p>
      )}

      {!periodoOk ? (
        <p className="aviso alerta">Informe as duas datas, com a inicial antes da final.</p>
      ) : isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : error ? (
        <p className="aviso erro">{error.message}</p>
      ) : !uso ? null : (
        <>
          <AvisosCusto custoDisponivel={uso.custoDisponivel !== false} modelosSemPreco={uso.modelosSemPreco ?? []} />
          <h3>
            Por função · {formatarData(uso.inicio)} a {formatarData(uso.fim)}
          </h3>
          <TabelaUso linhas={uso.porFuncao} nomesModulos={nomesModulos} />
          <p className="total-custo">
            Custo estimado do período:{" "}
            <strong>
              {uso.custoDisponivel === false
                ? "indisponível"
                : uso.custoEstimadoTotalUsd
                  ? formatarDolarTexto(uso.custoEstimadoTotalUsd)
                  : uso.custoEstimadoTotalUsd === null
                    ? "sem preço para todos os modelos"
                    : "—"}
            </strong>
          </p>
          <h3>Por mês</h3>
          <TabelaUso linhas={uso.porMes} nomesModulos={nomesModulos} comMes />
        </>
      )}
    </section>
  );
}

/** Por que o custo pode faltar: catálogo do rag fora do ar ou modelo sem preço no catálogo. */
function AvisosCusto({ custoDisponivel, modelosSemPreco }: { custoDisponivel: boolean; modelosSemPreco: string[] }) {
  return (
    <>
      {!custoDisponivel && <p className="aviso alerta">Custo indisponível: o serviço de IA não respondeu.</p>}
      {custoDisponivel && modelosSemPreco.length > 0 && (
        <p className="aviso alerta">
          Sem preço no catálogo para {modelosSemPreco.join(", ")}: o custo dessas linhas e o total não foram estimados.
        </p>
      )}
    </>
  );
}

/** Célula de custo: traço sem tokens ou sem custo calculado; "sem preço" quando o modelo não tem preço. */
function textoCusto(linha: TotalUso): string {
  if (linha.tokensEntrada === 0 && linha.tokensSaida === 0) return "—";
  if (linha.custoEstimadoUsd === null) return "sem preço";
  return linha.custoEstimadoUsd ? formatarDolarTexto(linha.custoEstimadoUsd) : "—";
}

interface PropsTabela {
  linhas: TotalUso[];
  nomesModulos: Record<string, string>;
  comMes?: boolean;
}

function TabelaUso({ linhas, nomesModulos, comMes = false }: PropsTabela) {
  if (linhas.length === 0) return <p className="aviso">Nenhum uso registrado neste período.</p>;
  return (
    <div className="rolagem">
      <table className="tabela compacta">
        <thead>
          <tr>
            {comMes && <th>Mês</th>}
            <th>Módulo</th>
            <th>Função</th>
            <th className="numero">Registros</th>
            <th className="numero">Tokens de entrada</th>
            <th className="numero">Tokens de saída</th>
            <th className="numero">Arquivos</th>
            <th className="numero">Páginas</th>
            <th className="numero">Custo estimado (US$)</th>
          </tr>
        </thead>
        <tbody>
          {linhas.map((l) => (
            <tr key={`${l.mes ?? ""}-${l.modulo}-${l.funcao}`}>
              {comMes && <td>{formatarMesAbreviado(l.mes)}</td>}
              <td>{nomesModulos[l.modulo] ?? l.modulo}</td>
              <td>{rotulosFuncao[l.funcao] ?? l.funcao}</td>
              <td className="numero">{formatarInteiro(l.quantidade)}</td>
              <td className="numero">{formatarInteiro(l.tokensEntrada)}</td>
              <td className="numero">{formatarInteiro(l.tokensSaida)}</td>
              <td className="numero">{formatarInteiro(l.arquivos)}</td>
              <td className="numero">{formatarInteiro(l.paginas)}</td>
              <td className="numero">{textoCusto(l)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
