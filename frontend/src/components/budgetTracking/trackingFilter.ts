import type { SpendingProfile, TrackingGroup, TrackingLine } from "../../api/types";

/** Filters of the "Acompanhamento da PO" grid (RF-13.4). They only hide rows that already came from the API. */
export interface TrackingFilter {
  /** lineId of the group (1.1 to 1.8); null = every group. */
  groupId: string | null;
  /** "só linhas com estouro no mês": the chosen month (AAAA-MM); null = filter off. */
  overrunMonth: string | null;
  /** "só linhas com saldo negativo no exercício". */
  negativeBalance: boolean;
  /** Spending profile as shown on the screen; null = every profile. */
  profile: SpendingProfile | null;
}

export const emptyFilter: TrackingFilter = { groupId: null, overrunMonth: null, negativeBalance: false, profile: null };

/**
 * Profile shown on the screen: the one recorded (suggested or confirmed) or, without it, the one in force.
 * Unconfirmed profiles count as the current suggestion in the API (effectiveProfile); the screen marks them as "tipo não confirmado".
 */
export const shownProfile = (line: TrackingLine): SpendingProfile => line.spendingProfile ?? line.effectiveProfile;

function passes(line: TrackingLine, filter: TrackingFilter): boolean {
  if (filter.profile && shownProfile(line) !== filter.profile) return false;
  if (filter.negativeBalance && !(line.balance != null && line.balance < 0)) return false;
  if (filter.overrunMonth) {
    const cell = line.cells.find((c) => c.month === filter.overrunMonth);
    // "difference" comes from the API as actual − planned: positive means above planned in the month
    if (!(cell?.difference != null && cell.difference > 0)) return false;
  }
  return true;
}

/** Groups with only the lines that pass the filter; a group left without lines disappears. */
export function filterGroups(groups: TrackingGroup[], filter: TrackingFilter): TrackingGroup[] {
  return groups
    .filter((g) => !filter.groupId || g.lineId === filter.groupId)
    .map((g) => ({ ...g, lines: g.lines.filter((l) => passes(l, filter)) }))
    .filter((g) => g.lines.length > 0);
}

export const isFiltering = (filter: TrackingFilter) =>
  filter.overrunMonth !== null || filter.negativeBalance || filter.profile !== null;
