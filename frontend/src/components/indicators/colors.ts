/**
 * Neutral colors for several series (groups, fiscal years, planned × collected), defined in styles.css
 * with a dark theme version. None is red: red is only for the overrun above 20% (RF-11.13).
 */
const COUNT = 8;

export const seriesColor = (index: number) => `var(--series-${(index % COUNT) + 1})`;
