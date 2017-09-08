package hu.akarnokd.kotlin

import io.reactivex.Flowable
import io.reactivex.FlowableSubscriber
import io.reactivex.internal.schedulers.SingleScheduler
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ClosedReceiveChannelException
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.system.measureTimeMillis

fun main(arg: Array<String>) = runBlocking<Unit> {

    val ctx1 = newSingleThreadContext("A");
    val ctx2 = newSingleThreadContext("B");

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

    println(measureTimeMillis {
        Flowable.range(1, 1_000_000)
                .subscribeOn(SingleScheduler())
                .take(500_000)
                .observeOn(SingleScheduler(), false, 1)
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
                .subscribeOn(SingleScheduler())
                .take(500_000)
                .observeOn(SingleScheduler())
                .map({
                    if ((it % 50000) == 0) {
                        println("${it} on ${Thread.currentThread().name }")
                    }
                    it + 1 })
                .await()
    })

    println("Done 2b")
}

interface CoFlow<out T> {

    suspend fun subscribe(consumer: CoConsumer<T>)
}

interface CoConnection {
    suspend fun close();
}

interface CoConsumer<in T> {

    suspend fun onSubscribe(conn : CoConnection)

    suspend fun onNext(t: T)

    suspend fun onError(t: Throwable)

    suspend fun onComplete()
}

class BooleanConnection : CoConnection {

    @Volatile
    var cancelled : Boolean = false;

    suspend override fun close() {
        cancelled = true;
    }

}

class Just<out T>(private val value : T) : CoFlow<T> {
    suspend override fun subscribe(consumer: CoConsumer<T>) {
        val conn = BooleanConnection()
        consumer.onSubscribe(conn);
        if (!conn.cancelled) {
            consumer.onNext(value)
        }
        if (!conn.cancelled) {
            consumer.onComplete()
        }
    }
}

class FromArray<out T>(private val values: Array<T>) : CoFlow<T> {
    suspend override fun subscribe(consumer: CoConsumer<T>) {
        val conn = BooleanConnection()
        consumer.onSubscribe(conn)
        for (v in values) {
            if (conn.cancelled) {
                return
            }
            consumer.onNext(v)
        }
        if (!conn.cancelled) {
            consumer.onComplete()
        }
    }
}


class Range(private val start : Int, val count : Int) : CoFlow<Int> {
    suspend override fun subscribe(consumer: CoConsumer<Int>) {
        val conn = BooleanConnection()
        consumer.onSubscribe(conn)
        for (v in start until start + count) {
            if (conn.cancelled) {
                return
            }
            consumer.onNext(v)
        }
        if (!conn.cancelled) {
            consumer.onComplete()
        }
    }
}

class SequentialConnection : AtomicReference<CoConnection?>(), CoConnection {

    object Disconnected : CoConnection {
        suspend override fun close() {
        }
    }

    suspend fun replace(conn: CoConnection?) : Boolean {
        while (true) {
            val a = get();
            if (a == Disconnected) {
                conn?.close()
                return false
            }
            if (compareAndSet(a, conn)) {
                return true;
            }
        }
    }

    suspend fun update(conn: CoConnection?) : Boolean {
        while (true) {
            val a = get();
            if (a == Disconnected) {
                conn?.close()
                return false
            }
            if (compareAndSet(a, conn)) {
                a?.close()
                return true;
            }
        }
    }

    suspend override fun close() {
        getAndSet(Disconnected)?.close()
    }
}

suspend fun <T> CoFlow<T>.subscribe(
        onNextHandler : suspend (T) -> Unit,
        onErrorHandler : (suspend (Throwable) -> Unit)? = null,
        onCompleteHandler : (suspend () -> Unit)? = null
) : CoConnection {
    val connection = SequentialConnection()
    this.subscribe(object : CoConsumer<T> {
        suspend override fun onSubscribe(conn: CoConnection) {
            connection.replace(conn);
        }

        suspend override fun onNext(t: T) {
            onNextHandler(t)
        }

        suspend override fun onError(t: Throwable) {
            onErrorHandler?.invoke(t)
        }

        suspend override fun onComplete() {
            onCompleteHandler?.invoke()
        }
    })
    return connection
}

suspend fun <T> CoFlow<T>.take(n: Long) : CoFlow<T> {
    val source = this;
    return object: CoFlow<T> {
        suspend override fun subscribe(consumer: CoConsumer<T>) {
            source.subscribe(object: CoConsumer<T> {

                var upstream: CoConnection? = null
                var remaining = n;

                suspend override fun onSubscribe(conn: CoConnection) {
                    upstream = conn;
                    consumer.onSubscribe(conn)
                }

                suspend override fun onNext(t: T) {
                    var r = remaining;
                    if (r != 0L) {
                        consumer.onNext(t);
                        remaining = --r
                        if (r == 0L) {
                            upstream!!.close()

                            consumer.onComplete()
                        }
                    }
                }

                suspend override fun onError(t: Throwable) {
                    consumer.onError(t)
                }

                suspend override fun onComplete() {
                    if (remaining != 0L) {
                        consumer.onComplete()
                    }
                }
            })
        }
    }
}

suspend fun <T> CoFlow<T>.subscribeOn(context: CoroutineContext) : CoFlow<T> {
    val source = this;
    return object: CoFlow<T> {
        suspend override fun subscribe(consumer: CoConsumer<T>) {
            launch(context) {
                source.subscribe(consumer)
            }
        }
    }
}

suspend fun <T> CoFlow<T>.observeOn(context: CoroutineContext, capacity : Int = 0) : CoFlow<T> {
    val source = this;
    return object: CoFlow<T> {
        suspend override fun subscribe(consumer: CoConsumer<T>) {
            val ch = if (capacity == 0) Channel<T>() else Channel<T>(capacity)
            val error = AtomicReference<Throwable>()

            source.subscribe(object : CoConsumer<T> {
                suspend override fun onNext(t: T) {
                    ch.send(t)
                }

                suspend override fun onError(t: Throwable) {
                    ch.close(t)
                }

                suspend override fun onComplete() {
                    ch.close()
                }

                suspend override fun onSubscribe(conn: CoConnection) {
                    consumer.onSubscribe(conn)
                }
            })

            launch(context) {
                try {
                    for (item in ch) {
                        consumer.onNext(item)
                    }
                } catch (ex: ClosedReceiveChannelException) {
                    // expected closing
                } catch (e: Throwable) {
                    consumer.onError(e)
                    return@launch
                }
                consumer.onComplete()
            }
        }
    }
}

suspend fun <T, R> CoFlow<T>.map(mapper: suspend (T) -> R) : CoFlow<R> {
    val source = this;
    return object: CoFlow<R> {
        suspend override fun subscribe(consumer: CoConsumer<R>) {
            source.subscribe(object : CoConsumer<T> {

                var upstream: CoConnection? = null;
                var done: Boolean = false;

                suspend override fun onNext(t: T) {
                    val r : R;

                    try {
                        r = mapper(t);
                    } catch (ex: Throwable) {
                        done = true;
                        upstream!!.close()
                        return
                    }
                    consumer.onNext(r)
                }

                suspend override fun onError(t: Throwable) {
                    if (!done) {
                        consumer.onError(t)
                    }
                }

                suspend override fun onComplete() {
                    if (!done) {
                        consumer.onComplete()
                    }
                }

                suspend override fun onSubscribe(conn: CoConnection) {
                    upstream = conn;
                    consumer.onSubscribe(conn)
                }
            })
        }
    }
}

suspend fun CoFlow<Any>.subscribe() {

    val ch = Channel<Unit>()

    this.subscribe(object : CoConsumer<Any> {
        suspend override fun onSubscribe(conn: CoConnection) {
        }

        suspend override fun onNext(t: Any) {
        }

        suspend override fun onError(t: Throwable) {
            ch.close(t)
        }

        suspend override fun onComplete() {
            ch.close()
        }
    })

    try {
        ch.receive()
    } catch (ex: ClosedReceiveChannelException) {
        // ignored
    }
}

suspend fun <T> Flowable<T>.await() {
    val source = this;

    suspendCancellableCoroutine<Unit> { cont ->

        source.subscribe(object : FlowableSubscriber<T> {
            override fun onSubscribe(conn: Subscription) {
                cont.disposeOnCompletion(object: DisposableHandle {
                    override fun dispose() {
                        conn.cancel()
                    }
                })
                conn.request(Long.MAX_VALUE)
            }

            override fun onNext(t: T) {
            }

            override fun onError(t: Throwable) {
                cont.resumeWithException(t)
            }

            override fun onComplete() {
                cont.resume(Unit)
            }
        })

    }
}