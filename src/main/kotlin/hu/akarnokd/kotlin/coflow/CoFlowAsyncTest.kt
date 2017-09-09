package hu.akarnokd.kotlin.coflow;

import io.reactivex.Flowable
import io.reactivex.internal.schedulers.SingleScheduler
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import kotlin.system.measureTimeMillis

fun main(arg: Array<String>) = runBlocking<Unit> {

    val ctx1 = newSingleThreadContext("A")
    val ctx2 = newSingleThreadContext("B")

    println(measureTimeMillis {
        Range(1, 1_000_000)
                .subscribeOn(ctx1)
                .take(500_000)
                .observeOn(ctx2)
                .map({
                    if ((it % 50000) == 0) {
                        println("${it} on ${Thread.currentThread().name}")
                    }
                    it + 1
                })
                .subscribe()
    })
    println("Done 1a")

    println(measureTimeMillis {
        Range(1, 1_000_000)
                .subscribeOn(ctx1)
                .take(500_000)
                .observeOn(ctx2, 1)
                .map({
                    if ((it % 50000) == 0) {
                        println("${it} on ${Thread.currentThread().name}")
                    }
                    it + 1
                })
                .subscribe()
    })
    println("Done 1b")

    println(measureTimeMillis {
        Range(1, 1_000_000)
                .subscribeOn(ctx1)
                .take(500_000)
                .observeOn(ctx2, 128)
                .map({
                    if ((it % 50000) == 0) {
                        println("${it} on ${Thread.currentThread().name}")
                    }
                    it + 1
                })
                .subscribe()
    })

    println("Done 1c")

    val sch1 = SingleScheduler()
    val sch2 = SingleScheduler()

    println(measureTimeMillis {
        Flowable.range(1, 1_000_000)
                .subscribeOn(sch1)
                .take(500_000)
                .observeOn(sch2, false, 1)
                .map({
                    if ((it % 50000) == 0) {
                        println("${it} on ${Thread.currentThread().name }")
                    }
                    it + 1 })
                .await()
    })

    println("Done 2a")

    println(measureTimeMillis {
        Flowable.range(1, 1_000_000)
                .subscribeOn(sch1)
                .take(500_000)
                .observeOn(sch2)
                .map({
                    if ((it % 50000) == 0) {
                        println("${it} on ${Thread.currentThread().name }")
                    }
                    it + 1 })
                .await()
    })

    println("Done 2b")
}