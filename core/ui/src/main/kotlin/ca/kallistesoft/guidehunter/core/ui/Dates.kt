package ca.kallistesoft.guidehunter.core.ui

import android.content.Context
import android.text.format.DateUtils
import ca.kallistesoft.guidehunter.core.model.Departure
import ca.kallistesoft.guidehunter.core.model.Slot
import ca.kallistesoft.guidehunter.core.model.Trip
import ca.kallistesoft.guidehunter.core.model.TripLength
import java.util.Formatter
import kotlinx.datetime.LocalDate

// Dates as the app writes them, in its language: "May 20 – 26, 2027", "May 20, 2027, morning", "1 week".

private const val DAY_MILLIS = 86_400_000L

private fun LocalDate.utcMillis() = toEpochDays() * DAY_MILLIS

/** [start] to [end], the month or year written once when shared ("May 20 – 26, 2027"); one day when they're equal. */
fun Context.dateRange(start: LocalDate, end: LocalDate): String {
    val locale = resources.configuration.locales[0]
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
    // The end is exclusive past midnight: + 1 day keeps the last day in. Dates are days, so UTC everywhere.
    return DateUtils.formatDateRange(this, Formatter(StringBuilder(), locale), start.utcMillis(), end.utcMillis() + 1, flags, "UTC")
        .toString()
}

fun Context.date(date: LocalDate): String = dateRange(date, date)

/** A trip's dates, with its half of the day: "May 20 – 26, 2027", "May 20, 2027, morning". */
fun Context.tripDates(trip: Trip): String = withSlot(dateRange(trip.start, trip.end), trip.slot)

/** A departure as the guide listed it: "May 20, 2027", "May 20, 2027, morning". */
fun Context.departureDate(departure: Departure): String = withSlot(date(departure.date), departure.slot)

private fun Context.withSlot(dates: String, slot: Slot?): String = when (slot) {
    Slot.MORNING -> getString(R.string.slot_morning_of, dates)
    Slot.AFTERNOON -> getString(R.string.slot_afternoon_of, dates)
    null -> dates
}

/** How long a trip lasts: "Half day", "3 days", "1 week", "2 weeks", "10 days". */
fun Context.tripLength(length: TripLength): String = when (length) {
    TripLength.HalfDay -> getString(R.string.trip_half_day)
    is TripLength.Days -> if (length.count % 7 == 0) {
        resources.getQuantityString(R.plurals.trip_weeks, length.count / 7, length.count / 7)
    } else {
        resources.getQuantityString(R.plurals.trip_days, length.count, length.count)
    }
}
