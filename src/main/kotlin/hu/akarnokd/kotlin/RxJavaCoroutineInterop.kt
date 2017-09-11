package hu.akarnokd.kotlin

import hu.akarnokd.kotlin.coflow.*
import io.reactivex.Flowable
import io.reactivex.FlowableSubscriber
import io.reactivex.internal.disposables.SequentialDisposable
import io.reactivex.internal.subscriptions.SubscriptionHelper
import io.reactivex.internal.util.BackpressureHelper
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

interface SuspendEmitter<in T> : CoroutineScope {

    suspend fun onNext(t: T)

    suspend fun onError(t: Throwable)

    suspend fun onComplete()
}

fun <T> produceFlow(producer: suspend SuspendEmitter<T>.() -> Unit) : Flowable<T> {
    return Produce(producer)
}

fun <T, R> Flowable<T>.transform(transformer: suspend SuspendEmitter<R>.(T) -> Unit) : Flowable<R> {
    return Transform(this, transformer)
}

val DONE = Object()

suspend fun <T> Flowable<T>.toReceiver(capacityHint: Int = 128) : ReceiveChannel<T> {
    val queue = SpscArrayChannel<Any>(capacityHint)

    val upstream = AtomicReference<Subscription>()
    val error = AtomicReference<Throwable>()
    val wip = AtomicInteger()

    subscribe(object: FlowableSubscriber<T> {

        override fun onSubscribe(s: Subscription) {
            if (SubscriptionHelper.setOnce(upstream, s)) {
                s.request(1)
            }
        }

        override fun onNext(t: T) {
            launch (Unconfined) {
                wip.getAndIncrement()
                queue.send(t as Any);

                if (wip.decrementAndGet() == 0) {
                    upstream.get().request(1)
                } else {
                    queue.send(DONE);
                }
            }
        }

        override fun onComplete() {
            if (wip.getAndIncrement() == 0) {
                launch(Unconfined) {
                    queue.send(DONE);
                }
            }
        }

        override fun onError(t: Throwable) {
            error.lazySet(t)
            onComplete()
        }

    })

    return produce(Unconfined) {
        coroutineContext[Job]?.invokeOnCompletion { SubscriptionHelper.cancel(upstream) }
        while (true) {
            val o = queue.receive()
            if (o == DONE) {
                val ex = error.get()
                if (ex != null) {
                    throw ex
                }
                break
            }
            send(o as T)
        }
    }
}


class Transform<T, R>(private val source: Flowable<T>, private val transformer: suspend SuspendEmitter<R>.(T) -> Unit) : Flowable<R>() {
    override fun subscribeActual(s: Subscriber<in R>) {
        val ctx = newCoroutineContext(Unconfined)
        val parent = ProduceWithResource(s, ctx)
        s.onSubscribe(parent)
        source.subscribe(object: FlowableSubscriber<T> {

            var upstream : Subscription? = null

            val wip = AtomicInteger()
            var error: Throwable? = null

            override fun onSubscribe(s: Subscription) {
                upstream = s
                parent.setResource(s)
                s.request(1)
            }

            override fun onNext(t: T) {
                launch(ctx) {
                    parent.setJob(coroutineContext[Job])
                    wip.getAndIncrement()

                    transformer(parent, t)

                    if (wip.decrementAndGet() == 0) {
                        upstream!!.request(1)
                    } else {
                        val ex = error;
                        if (ex == null) {
                            s.onComplete()
                        } else {
                            s.onError(ex)
                        }
                        parent.cancel()
                    }
                }
            }

            override fun onError(t: Throwable) {
                error = t
                if (wip.getAndIncrement() == 0) {
                    s.onError(t)
                    parent.cancel()
                }
            }

            override fun onComplete() {
                if (wip.getAndIncrement() == 0) {
                    s.onComplete()
                    parent.cancel()
                }
            }
        })
    }
}

class Produce<T>(private val producer: suspend SuspendEmitter<T>.() -> Unit) : Flowable<T>() {
    override fun subscribeActual(s: Subscriber<in T>) {
        launch(Unconfined) {
            val parent = ProduceSubscription(s, coroutineContext)
            parent.setJob(coroutineContext[Job])
            s.onSubscribe(parent)
            producer(parent)
        }
    }
}

open class ProduceSubscription<T>(
        private val actual: Subscriber<in T>,
        private val ctx : CoroutineContext
) : Subscription, SuspendEmitter<T> {

    companion object {
        val CANCELLED = Object()
    }

    @Suppress("DEPRECATION")
    override val context: CoroutineContext
        get() = ctx

    override val isActive: Boolean
        get() = job.get() != CANCELLED


    private val job = AtomicReference<Any>()

    private val requested = AtomicLong()

    private val resume = AtomicReference<Cont?>()

    private var done: Boolean = false

    override suspend fun onNext(t: T) {
        if (job.get() == CANCELLED) {
            suspendCoroutine<Unit> { }
        }
        val r = requested.get()
        if (r == 0L) {
            suspendCoroutine<Unit> { cont -> await(resume, cont)  }
        }

        actual.onNext(t)

        if (job.get() == CANCELLED) {
            suspendCoroutine<Unit> { }
        }
        if (r == 1L && resume.get() == TOKEN) {
            resume.compareAndSet(TOKEN, null)
        }
        if (r != Long.MAX_VALUE) {
            requested.decrementAndGet()
        }
    }

    override suspend fun onError(t: Throwable) {
        if (!done) {
            done = true
            actual.onError(t)
            cancel()
        }
        suspendCoroutine<Unit> { }
    }

    override suspend fun onComplete() {
        if (!done) {
            done = true
            actual.onComplete()
            cancel()
        }
        suspendCoroutine<Unit> { }
    }

    override fun cancel() {
        val o = job.getAndSet(CANCELLED)
        if (o != CANCELLED) {
            (o as Job).cancel()
        }
    }

    fun setJob(j: Job?) {
        while (true) {
            val o = job.get()
            if (o == CANCELLED) {
                j?.cancel()
                break
            }
            if (job.compareAndSet(o, j)) {
                break
            }
        }
    }


    override fun request(n: Long) {
        if (BackpressureHelper.add(requested, n) == 0L) {
            notify(resume)
        }
    }
}

class ProduceWithResource<T>(
        actual: Subscriber<in T>,
        ctx : CoroutineContext
) : ProduceSubscription<T>(actual, ctx) {
    private val resource = AtomicReference<Subscription>()

    fun setResource(s: Subscription) {
        SubscriptionHelper.replace(resource, s)
    }

    override fun cancel() {
        SubscriptionHelper.cancel(resource)
        super.cancel()
    }
}