package ca.kallistesoft.guidehunter.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** How long one of an offer's trips lasts: half a day (a morning or an afternoon) or a number of days. */
sealed interface TripLength {
    /** Days on the calendar the trip covers: a half day and one day both fit in one. */
    val days: Int

    data object HalfDay : TripLength {
        override val days = 1
    }

    data class Days(val count: Int) : TripLength {
        override val days get() = count
    }

    companion object {
        /** The longest trip a guide can enter: a little over eight weeks. */
        const val MAX_DAYS = 60
    }
}

/** Which half of the day a half-day trip leaves. */
enum class Slot { MORNING, AFTERNOON }

/** A day a trip leaves; [slot] only on half-day offers. Sorted by date, the morning first. */
data class Departure(val date: LocalDate, val slot: Slot? = null) : Comparable<Departure> {
    override fun compareTo(other: Departure): Int =
        compareValuesBy(this, other, { it.date }, { it.slot?.ordinal ?: -1 })
}

/** A trip as the hunter sees it: from [start] to [end] (the same day for a half day or a day), [slot] for a half day. */
data class Trip(val start: LocalDate, val end: LocalDate = start, val slot: Slot? = null) {
    /** This trip lies within [free] (the dates a hunter can go). */
    fun within(free: Trip): Boolean = start >= free.start && end <= free.end
}

/**
 * When an offer's trips leave: their [length] and the [departures] the guide listed, either on those dates only or
 * on the same days [everyYear]. No departures: "dates on request".
 */
data class Schedule(
    val length: TripLength? = null,
    val departures: List<Departure> = emptyList(),
    val everyYear: Boolean = false,
) {
    val onRequest: Boolean get() = departures.isEmpty()

    /** Every departure is over (dates only): the offer leaves the map until the guide adds new ones. */
    fun expired(today: LocalDate): Boolean = !everyYear && departures.isNotEmpty() && departures.all { it.date < today }

    /** The trips still to come, soonest first; every year, each departure's next occurrence. */
    fun trips(today: LocalDate): List<Trip> {
        val upcoming = if (everyYear) {
            departures.map { it.copy(date = it.date.nextOccurrence(today)) }
        } else {
            departures.filter { it.date >= today }
        }
        return upcoming.distinct().sorted().map(::trip)
    }

    /** A trip still to come lies within [free]: every year, on any year [free] touches. */
    fun fits(free: Trip, today: LocalDate): Boolean {
        val candidates = if (everyYear) {
            // The year before too: a week leaving Dec 30 ends in the next year.
            (free.start.year - 1..free.end.year).flatMap { year -> departures.map { it.copy(date = it.date.inYear(year)) } }
        } else {
            departures
        }
        return candidates.any { it.date >= today && trip(it).within(free) }
    }

    private fun trip(departure: Departure) =
        Trip(departure.date, departure.date.plus((length?.days ?: 1) - 1, DateTimeUnit.DAY), departure.slot)
}

/** The same day in [year]; February 29 becomes the 28th in a year without it. */
internal fun LocalDate.inYear(year: Int): LocalDate =
    if (month == Month.FEBRUARY && day == 29 && !isLeap(year)) LocalDate(year, 2, 28) else LocalDate(year, month.number, day)

/** This day's next occurrence from [today] on (today included). */
internal fun LocalDate.nextOccurrence(today: LocalDate): LocalDate =
    inYear(today.year).let { if (it >= today) it else inYear(today.year + 1) }

private fun isLeap(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

/** Today's date where the user is: what makes departures upcoming or past. A fixed day in tests. */
fun interface Today {
    operator fun invoke(): LocalDate
}
