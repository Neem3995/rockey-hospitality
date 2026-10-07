// Display context only. Backend timestamps/lifecycle semantics remain authoritative.
export const HOTEL_TIME_ZONE = 'America/New_York';

const hotelTime = new Intl.DateTimeFormat('en-US', {
  timeZone: HOTEL_TIME_ZONE, dateStyle: 'medium', timeStyle: 'short',
});

/** Format an offset-bearing server instant for display only. @param {string} instant */
export function formatHotelTime(instant) {
  return hotelTime.format(new Date(instant));
}
