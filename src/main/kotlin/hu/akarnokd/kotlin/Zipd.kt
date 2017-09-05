package hu.akarnokd.kotlin

import hu.akarnokd.rxjava2.schedulers.BlockingScheduler
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun main(arg: Array<String>) = runBlocking<Unit> {
    println("-------------")
    println("Coroutine Way")
    println("-------------")
    coroutineWay()

    Thread.sleep(1000)

    println()
    println("------------")
    println("Reactive Way")
    println("------------")
    reactiveWay()
}

suspend fun coroutineWay() {
    val t0 = System.currentTimeMillis()

    var i = 0;
    while (true) {
        println("Attempt " + (i + 1) + " at T=" + (System.currentTimeMillis() - t0))
        var v1 = async(CommonPool) { f1(i) }
        var v2 = async(CommonPool) { f2(i) }

        var v3 = launch(CommonPool) {
            Thread.sleep(500)
            println("    Cancelling at T=" + (System.currentTimeMillis() - t0))
            val te = TimeoutException();
            v1.cancel(te);
            v2.cancel(te);
        }

        try {
            val r1 = v1.await();
            val r2 = v2.await();
            v3.cancel();
            println(r1 + r2)
            break;
        } catch (ex: TimeoutException) {
            println("         Crash at T=" + (System.currentTimeMillis() - t0))
            if (++i > 2) {
                throw ex;
            }
        }
    }
    println("End at T=" + (System.currentTimeMillis() - t0))

}

fun reactiveWay() {
    RxJavaPlugins.setErrorHandler({ })

    val sched = BlockingScheduler()
    sched.execute {
        val t0 = System.currentTimeMillis()
        val count = Array<Int>(1, { 0 })

        Single.defer({
            val c = count[0]++;
            println("Attempt " + (c + 1) + " at T=" + (System.currentTimeMillis() - t0))

            Single.zip(
                    Single.fromCallable({ f3(c) }).subscribeOn(Schedulers.io()),
                    Single.fromCallable({ f4(c) }).subscribeOn(Schedulers.io()),
                    BiFunction<Int, Int, Int> { a, b -> a + b }
            )
        })
        .doOnDispose({
            println("    Cancelling at T=" + (System.currentTimeMillis() - t0))
        })
        .timeout(500, TimeUnit.MILLISECONDS)
        .retry({ x, e ->
            println("         Crash at " + (System.currentTimeMillis() - t0))
            x < 3 && e is TimeoutException
        })
        .doAfterTerminate { sched.shutdown() }
        .subscribe({
            println(it)
            println("End at T=" + (System.currentTimeMillis() - t0))
        },
        { it.printStackTrace() })
    }
}

suspend fun f1(i: Int) : Int {
    Thread.sleep(if (i != 2) 2000L else 200L)
    return 1
}

suspend fun f2(i: Int) : Int {
    Thread.sleep(if (i != 2) 2000L else 200L)
    return 2
}

fun f3(i: Int) : Int {
    Thread.sleep(if (i != 2) 2000L else 200L)
    return 1
}

fun f4(i: Int) : Int {
    Thread.sleep(if (i != 2) 2000L else 200L)
    return 2
}