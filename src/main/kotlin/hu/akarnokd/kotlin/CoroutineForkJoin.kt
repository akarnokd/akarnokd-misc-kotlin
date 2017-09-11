package hu.akarnokd.kotlin

import kotlinx.coroutines.experimental.*
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object CoroutineForkJoin {
    @JvmStatic
    fun main(args: Array<String>) {

        launch(Unconfined) {
            val ctx = coroutineContext
            handler = { c ->
                launch(Unconfined) {
                    println(c)

                    launch(ctx) {
                        //delay(1)
                        println("$c - after")
                    }
                            .join()
                    println("$c - done")
                    request()
                }
            }


            request()
        }

        Thread.sleep(10000)
    }

    val req = AtomicLong()
    var n = 0
    var handler: ((Int) -> Unit)? = null

    fun request() {
        if (req.getAndIncrement() == 0L) {
            do {
                handler?.invoke(n++)
            } while (req.decrementAndGet() != 0L)
        }
    }
}