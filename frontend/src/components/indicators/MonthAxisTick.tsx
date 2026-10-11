interface Props {
  x?: number;
  y?: number;
  payload?: { value: string };
}

/**
 * X axis tick over several lines: the first is the month ("set/26"); the others are neutral notes
 * ("sem fluxo carregado", "provisório"). The text comes ready from `monthAxisLabel`.
 */
export function MonthAxisTick({ x, y, payload }: Props) {
  const [month, ...notes] = (payload?.value ?? "").split("\n");
  return (
    <text x={x} y={y} textAnchor="middle" className="axis-label">
      <tspan x={x} dy={14}>
        {month}
      </tspan>
      {notes.map((note, i) => (
        <tspan key={i} x={x} dy={13} className="axis-label-note">
          {note}
        </tspan>
      ))}
    </text>
  );
}

/** Height of the X axis with the month and up to two notes below it. */
export const MONTH_AXIS_HEIGHT = 60;
