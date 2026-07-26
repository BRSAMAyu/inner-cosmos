import type { ChangeEventHandler, InputHTMLAttributes } from "react";
import type { Locale } from "../../i18n";

type TemporalInputType = "date" | "datetime-local" | "time";

type LocalizedTemporalInputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "aria-label" | "lang" | "onChange" | "type"
> & {
  label: string;
  locale: Locale;
  onChange: ChangeEventHandler<HTMLInputElement>;
  type: TemporalInputType;
};

/**
 * Gives native calendar/clock controls the app locale instead of inheriting the
 * browser's surrounding document language, while requiring an accessible name.
 */
export function LocalizedTemporalInput({
  label,
  locale,
  ...inputProps
}: LocalizedTemporalInputProps) {
  return <input {...inputProps} aria-label={label} lang={locale} />;
}
