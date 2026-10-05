import { Link } from "react-router";
import type { DadoGravado } from "../../api/tipos";

/** Nome legível de cada consulta numérica (as ferramentas que o rag chama no backend). */
const nomesConsultas: Record<string, string> = {
  resumo_fundos: "Resumo dos fundos",
  buscar_lancamentos: "Lançamentos",
  listar_arquivos: "Arquivos enviados",
  conferencias_do_arquivo: "Conferências do arquivo",
};

const valorParametro = (dado: DadoGravado, ...nomes: string[]) =>
  dado.parametros.find((p) => nomes.includes(p.nome))?.valor;

/**
 * Tela onde a pessoa confere o número (RF-04.14). Só rotas que existem hoje; sem tela, sem link.
 * Lançamentos ainda não têm tela própria: aparece só o nome da consulta.
 */
function linkDaConsulta(dado: DadoGravado): { para: string; rotulo: string } | null {
  switch (dado.consulta) {
    case "resumo_fundos":
      return { para: "/", rotulo: "Ver saldo por fundo no Início" };
    case "listar_arquivos":
      return { para: "/arquivos", rotulo: "Ver em Arquivos" };
    case "conferencias_do_arquivo": {
      const arquivoId = valorParametro(dado, "arquivoId", "arquivo_id");
      return { para: arquivoId ? `/arquivos?arquivo=${encodeURIComponent(arquivoId)}` : "/arquivos", rotulo: "Ver conferências do arquivo" };
    }
    default:
      return null;
  }
}

/** Bloco "Nos dados gravados": números vindos das consultas ao banco, já formatados pela API (nada é calculado aqui). */
export function DadosGravados({ dados }: { dados: DadoGravado[] }) {
  return (
    <section className="bloco-resposta" aria-label="Nos dados gravados">
      <h3>Nos dados gravados</h3>
      {dados.map((dado, i) => {
        const link = linkDaConsulta(dado);
        return (
          <div key={i} className="dado-gravado">
            <p className="discreto">
              Consulta: <strong>{nomesConsultas[dado.consulta] ?? dado.consulta}</strong>
              {dado.parametros.length > 0 && ` (${dado.parametros.map((p) => `${p.nome}: ${p.valor}`).join("; ")})`}
            </p>
            {dado.linhas.length > 0 && (
              <table className="tabela compacta">
                <tbody>
                  {dado.linhas.map((l, j) => (
                    <tr key={j}>
                      <td>{l.rotulo}</td>
                      <td className="numero">{l.valor}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {dado.comentario && <p>{dado.comentario}</p>}
            {link && (
              <Link to={link.para} className="botao-link">
                {link.rotulo}
              </Link>
            )}
          </div>
        );
      })}
    </section>
  );
}
