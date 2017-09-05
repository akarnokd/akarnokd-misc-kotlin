package hu.akarnokd.kotlin

import io.reactivex.Observable
import io.reactivex.functions.Function
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

/**
 * Created by akarnokd on 2017.06.20..
 */
fun nextSolarEclipse(after: LocalDate): Observable<LocalDate> {
    return Observable
            .just(
                    LocalDate.of(2016, Month.MARCH, 9),
                    LocalDate.of(2016, Month.SEPTEMBER, 1),
                    LocalDate.of(2017, Month.FEBRUARY, 26),
                    LocalDate.of(2017, Month.AUGUST, 21),
                    LocalDate.of(2018, Month.FEBRUARY, 15),
                    LocalDate.of(2018, Month.JULY, 13),
                    LocalDate.of(2018, Month.AUGUST, 11),
                    LocalDate.of(2019, Month.JANUARY, 6),
                    LocalDate.of(2019, Month.JULY, 2),
                    LocalDate.of(2019, Month.DECEMBER, 26)
            )
            .subscribeOn(Schedulers.single())
            .skipWhile { date ->
                !date.isAfter(after)
            }
            .zipWith(
                    Observable.interval(5000, 50, TimeUnit.MILLISECONDS),
                    BiFunction { date, _ -> date }
            )
}

fun main(args: Array<String>) {
    nextSolarEclipse(LocalDate.now())
            .timeout<Long, Long>(
                    Observable.timer(100, TimeUnit.MILLISECONDS).doOnNext({ System.out.println("Timeout main")}) ,
                    Function { ignore -> Observable.timer(40, TimeUnit.MILLISECONDS) }
            )
            .subscribe(
                    { println(it) },
                    { it.printStackTrace() },
                    { println("Completed") }
            )

    TimeUnit.MILLISECONDS.sleep(10000)
}