interface Props {
  x?: number;
  y?: number;
  payload?: { value: string };
}

/**
 * Marca do eixo X em várias linhas: a primeira é o mês ("set/26"); as demais são notas neutras
 * ("sem fluxo carregado", "provisório"). O texto vem pronto de `rotuloEixoMes`.
 */
export function EixoMes({ x, y, payload }: Props) {
  const [mes, ...notas] = (payload?.value ?? "").split("\n");
  return (
    <text x={x} y={y} textAnchor="middle" className="rotulo-eixo">
      <tspan x={x} dy={14}>
        {mes}
      </tspan>
      {notas.map((nota, i) => (
        <tspan key={i} x={x} dy={13} className="rotulo-eixo-nota">
          {nota}
        </tspan>
      ))}
    </text>
  );
}

/** Altura do eixo X com o mês e até duas notas embaixo. */
export const ALTURA_EIXO_MES = 60;
