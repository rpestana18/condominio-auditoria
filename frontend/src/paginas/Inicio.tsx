import { Link } from "react-router";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { usePainel } from "../api/consultas";
import type { Painel } from "../api/tipos";
import { CartaoNumero } from "../componentes/CartaoNumero";
import { useSessao } from "../contexto";
import { formatarData, formatarMoeda, formatarMoedaCurta, formatarPeriodo } from "../formato";

export function Inicio() {
  const { condominioId, pode } = useSessao();
  const { data: painel, isLoading, error } = usePainel(condominioId);

  if (isLoading) return <p className="aviso">Carregando…</p>;
  if (error) return <p className="aviso erro">{error.message}</p>;
  if (!painel) {
    return (
      <section className="vazio">
        <h1>Ainda não há números para mostrar</h1>
        <p>Os números aparecem aqui assim que o primeiro fluxo de caixa mensal for processado.</p>
        {pode("GESTOR", "ADMIN") && (
          <Link className="botao" to="/arquivos">
            Enviar arquivo
          </Link>
        )}
      </section>
    );
  }
  return <PainelDoMes painel={painel} />;
}

function PainelDoMes({ painel }: { painel: Painel }) {
  const resultado = painel.entradas - painel.saidas;
  // Só os 8 fundos com mais movimento, do maior para o menor; os demais aparecem na tabela abaixo
  const comMovimento = painel.fundos
    .filter((f) => f.entradas !== 0 || f.saidas !== 0)
    .sort((a, b) => Math.max(b.entradas, b.saidas) - Math.max(a.entradas, a.saidas))
    .slice(0, 8);
  const negativos = painel.fundos.filter((f) => f.saldoAtual < 0);

  return (
    <>
      <header className="titulo-pagina">
        <h1>Resumo de {formatarPeriodo(painel.periodoInicio, painel.periodoFim)}</h1>
        <span className={painel.conferenciasComFalha === 0 ? "selo ok" : "selo alerta"}>
          {painel.conferenciasComFalha === 0
            ? "Relatório conferido: todas as somas batem"
            : `${painel.conferenciasComFalha} conferência(s) não bateram`}
        </span>
      </header>

      <div className="cartoes">
        <CartaoNumero titulo="Saldo no início do mês" valor={painel.saldoAnterior} />
        <CartaoNumero titulo="Entradas" valor={painel.entradas} />
        <CartaoNumero titulo="Saídas" valor={painel.saidas} />
        <CartaoNumero
          titulo="Resultado do mês"
          valor={resultado}
          destaque={resultado >= 0 ? "positivo" : "negativo"}
          dica="Soma de todos os fundos, inclusive os de finalidade própria (energia, água, reserva)"
        />
        <CartaoNumero titulo="Saldo no fim do mês" valor={painel.saldoAtual} />
      </div>

      <section className="bloco">
        <h2>Entradas e saídas dos fundos com mais movimento</h2>
        <ResponsiveContainer width="100%" height={Math.max(260, comMovimento.length * 44)}>
          <BarChart data={comMovimento} layout="vertical" margin={{ left: 24, right: 24 }}>
            <CartesianGrid strokeDasharray="3 3" horizontal={false} />
            <XAxis type="number" tickFormatter={formatarMoedaCurta} />
            <YAxis type="category" dataKey="fundo" width={190} tick={{ fontSize: 12 }} />
            <Tooltip formatter={(valor) => formatarMoeda(Number(valor))} />
            <Legend />
            <Bar dataKey="entradas" name="Entradas" fill="var(--cor-entrada)" isAnimationActive={false} />
            <Bar dataKey="saidas" name="Saídas" fill="var(--cor-saida)" isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </section>

      <div className="duas-colunas">
        <section className="bloco">
          <h2>Saldo por fundo</h2>
          {negativos.length > 0 && (
            <p className="aviso alerta">
              {negativos.length} fundo(s) com saldo negativo: {negativos.map((f) => f.fundo).join(", ")}
            </p>
          )}
          <table className="tabela">
            <thead>
              <tr>
                <th>Fundo</th>
                <th className="numero">Resultado do mês</th>
                <th className="numero">Saldo atual</th>
              </tr>
            </thead>
            <tbody>
              {painel.fundos.map((f) => (
                <tr key={f.fundo}>
                  <td>{f.fundo}</td>
                  <td className={`numero ${f.resultado < 0 ? "negativo" : ""}`}>{formatarMoeda(f.resultado)}</td>
                  <td className={`numero ${f.saldoAtual < 0 ? "negativo" : ""}`}>{formatarMoeda(f.saldoAtual)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="bloco">
          <h2>Maiores despesas do mês</h2>
          <ol className="lista-despesas">
            {painel.maioresDespesas.map((d, i) => (
              <li key={i}>
                <div>
                  <strong>{formatarMoeda(d.valor)}</strong>
                  <span className="discreto">
                    {formatarData(d.data)} · {d.fundo} · pág. {d.pagina}
                  </span>
                </div>
                <span className="historico">{d.conta}</span>
                <span className="historico discreto">{d.historico}</span>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </>
  );
}
