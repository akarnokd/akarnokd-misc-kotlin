package hu.akarnokd.kotlin;

import io.reactivex.Flowable
import kotlinx.coroutines.experimental.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun main(arg: Array<String>) = runBlocking<Unit> {

    var win1 = 0
    var win2 = 0

    for (i in 0 until 1000) {
        val d1 = async(CommonPool) { coroutineWins() }
        val d2 = async(CommonPool) { reactiveWins() }

        d1.invokeOnCompletion { if (d2.isActive) win1++ }
        d2.invokeOnCompletion { if (d1.isActive) win2++ }

        d1.await()
        d2.await()
    }

    println("Votes, round 1:")
    println("  $win1 for Coroutines")
    println("  $win2 for Reactive")
    println("--------")
    println("  ${win1 + win2} votes")

    println("====================================")

    val win3 = Array(1, { 0 })
    val win4 = Array(1, { 0 })

    Flowable.range(1, 1000)
            .concatMap({
                Flowable.ambArray(
                        Flowable.just("Coroutines!").delay(1, TimeUnit.MILLISECONDS),
                        Flowable.just("Reactive!").delay(1, TimeUnit.MILLISECONDS)
                )
            })
            .doOnNext({ v -> if (v == "Coroutines!") win3[0]++ else win4[0]++ })
            .blockingLast()

    println("Votes, round 2:")
    println("  ${win3[0]} for Coroutines")
    println("  ${win4[0]} for Reactive")
    println("--------")
    println("  ${win3[0] + win4[0]} votes")

    println("====================================")

    win1 = 0
    win2 = 0

    for (i in 0 until 1000) {
        val d1 = async(CommonPool) { coroutineWins() }
        val d2 = async(CommonPool) { reactiveWins() }

        val once = AtomicBoolean();

        d1.invokeOnCompletion { if (once.compareAndSet(false, true)) win1++ }
        d2.invokeOnCompletion { if (once.compareAndSet(false, true)) win2++ }

        d1.await()
        d2.await()
    }

    println("Votes, round 3:")
    println("  $win1 for Coroutines")
    println("  $win2 for Reactive")
    println("--------")
    println("  ${win1 + win2} votes")


    println("====================================")

    win3[0] = 0
    win4[0] = 0

    val exec1 = Executors.newScheduledThreadPool(1)
    val exec2 = Executors.newScheduledThreadPool(1)

    for (i in 0 until 1000) {

        val f1 = CompletableFuture<String>()
        val f2 = CompletableFuture<String>()

        exec1.schedule({ f1.complete("Coroutines!") }, 1, TimeUnit.MILLISECONDS)
        exec2.schedule({ f1.complete("Reactive!") }, 1, TimeUnit.MILLISECONDS)

        CompletableFuture.anyOf(f1, f2)
                .whenComplete({ v, e -> if (v == "Coroutines!") win3[0]++ else win4[0]++})
                .join()
    }

    println("Votes, round 4:")
    println("  ${win3[0]} for Coroutines")
    println("  ${win4[0]} for Reactive")
    println("--------")
    println("  ${win3[0] + win4[0]} votes")

    exec1.shutdown()
    exec2.shutdown()
}

suspend fun coroutineWins() : String {
    delay(1)
    return "Coroutines!"
}

suspend fun reactiveWins() : String {
    delay(1)
    return "Reactive!"
}
