/**
 * Backend timestamps with Z / an explicit offset are absolute instants. Historical Inner Cosmos
 * rows may omit the suffix even though they were persisted in UTC; interpreting those as browser
 * local time shifts the ETA by eight hours on a typical demo laptop in China.
 */
export function parseSlowLetterInstant(value: string | null | undefined): Date | null {
  if (!value) return null;
  const normalized = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`;
  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function formatSlowLetterInstant(value: string, options: {
  locale?: string;
  timeZone?: string;
} = {}): string {
  const parsed = parseSlowLetterInstant(value);
  if (!parsed) return value;
  return new Intl.DateTimeFormat(options.locale, {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
    timeZoneName: "short", hour12: false, ...(options.timeZone ? { timeZone: options.timeZone } : {})
  }).format(parsed);
}

export function secondsUntilSlowLetterArrival(value: string, now = Date.now()): number {
  const parsed = parseSlowLetterInstant(value);
  return parsed ? Math.max(0, Math.ceil((parsed.getTime() - now) / 1000)) : 0;
}

export function toLocalDateTimeInputValue(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
