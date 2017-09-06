package hu.akarnokd.kotlin;

import io.reactivex.Flowable
import kotlinx.coroutines.experimental.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun main(arg: Array<String>) = runBlocking<Unit> {

    val win1 = Array(1, { 0 })
    val win2 = Array(1, { 0 })

    for (i in 0 until 1000) {
        val d1 = async(CommonPool) { coroutineWins() }
        val d2 = async(CommonPool) { reactiveWins() }

        d1.invokeOnCompletion { if (d2.isActive) win1[0]++ }
        d2.invokeOnCompletion { if (d1.isActive) win2[0]++ }

        d1.await()
        d2.await()
    }

    println("Votes, round 1:")
    println("  ${win1[0]} for Coroutines")
    println("  ${win2[0]} for Reactive")
    println("--------")
    println("  ${win1[0] + win2[0]} votes")

    println("====================================")

    win1[0] = 0
    win2[0] = 0

    Flowable.range(1, 1000)
            .concatMap({
                Flowable.ambArray(
                        Flowable.just("Coroutines!").delay(1, TimeUnit.MILLISECONDS),
                        Flowable.just("Reactive!").delay(1, TimeUnit.MILLISECONDS)
                )
            })
            .doOnNext({ v -> if (v == "Coroutines!") win1[0]++ else win2[0]++ })
            .blockingLast()

    println("Votes, round 2:")
    println("  ${win1[0]} for Coroutines")
    println("  ${win2[0]} for Reactive")
    println("--------")
    println("  ${win1[0] + win2[0]} votes")

    println("====================================")

    win1[0] = 0
    win2[0] = 0

    for (i in 0 until 1000) {
        val d1 = async(CommonPool) { coroutineWins() }
        val d2 = async(CommonPool) { reactiveWins() }

        val once = AtomicBoolean();

        d1.invokeOnCompletion { if (once.compareAndSet(false, true)) win1[0]++ }
        d2.invokeOnCompletion { if (once.compareAndSet(false, true)) win2[0]++ }

        d1.await()
        d2.await()
    }

    println("Votes, round 3:")
    println("  ${win1[0]} for Coroutines")
    println("  ${win2[0]} for Reactive")
    println("--------")
    println("  ${win1[0] + win2[0]} votes")


    println("====================================")

    win1[0] = 0
    win2[0] = 0

    val exec1 = Executors.newScheduledThreadPool(1)
    val exec2 = Executors.newScheduledThreadPool(1)

    for (i in 0 until 1000) {

        val f1 = CompletableFuture<String>()
        val f2 = CompletableFuture<String>()

        exec1.schedule({ f1.complete("Coroutines!") }, 1, TimeUnit.MILLISECONDS)
        exec2.schedule({ f1.complete("Reactive!") }, 1, TimeUnit.MILLISECONDS)

        CompletableFuture.anyOf(f1, f2)
                .whenComplete({ v, e -> if (v == "Coroutines!") win1[0]++ else win2[0]++})
                .join()
    }

    println("Votes, round 4:")
    println("  ${win1[0]} for Coroutines")
    println("  ${win2[0]} for Reactive")
    println("--------")
    println("  ${win1[0] + win2[0]} votes")

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
