import { getLang } from '../i18n'

/** An epoch day formatted in the app language. UTC so the date never shifts with the phone's time zone. */
export const formatDay = (day: number, o: Intl.DateTimeFormatOptions): string =>
  new Intl.DateTimeFormat(getLang(), { ...o, timeZone: 'UTC' }).format(new Date(day * 86_400_000))
