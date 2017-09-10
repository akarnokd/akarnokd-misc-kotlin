package hu.akarnokd.kotlin

import hu.akarnokd.kotlin.coflow.*
import io.reactivex.Flowable
import io.reactivex.FlowableSubscriber
import io.reactivex.internal.subscriptions.SubscriptionHelper
import io.reactivex.internal.util.BackpressureHelper
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.launch
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

interface SuspendEmitter<in T> : CoroutineScope {

    suspend fun onNext(t: T)

    suspend fun onError(t: Throwable)

    suspend fun onComplete()

    fun isCancelled() : Boolean
}

fun <T> produceFlow(producer: suspend SuspendEmitter<T>.() -> Unit) : Flowable<T> {
    return Produce(producer)
}

fun <T, R> Flowable<T>.transform(transformer: suspend SuspendEmitter<R>.(T) -> Unit) : Flowable<R> {
    return Transform(this, transformer)
}


class Transform<T, R>(private val source: Flowable<T>, private val transformer: suspend SuspendEmitter<R>.(T) -> Unit) : Flowable<R>() {
    override fun subscribeActual(s: Subscriber<in R>) {
        launch(Unconfined) {
            val parent = ProduceWithResource(s, coroutineContext)
            s.onSubscribe(parent)

            source.subscribe(object: FlowableSubscriber<T> {

                var upstream : Subscription? = null

                override fun onSubscribe(s: Subscription) {
                    upstream = s
                    parent.setResource(s)
                    s.request(1)
                }

                override fun onNext(t: T) {
                    launch(parent.coroutineContext) {
                        transformer(parent, t)
                        upstream!!.request(1)
                    }
                }

                override fun onError(t: Throwable) {
                    launch(parent.coroutineContext) {
                        s.onError(t)
                    }
                }

                override fun onComplete() {
                    launch(parent.coroutineContext) {
                        s.onComplete()
                    }
                }
            })
        }
    }
}

class Produce<T>(private val producer: suspend SuspendEmitter<T>.() -> Unit) : Flowable<T>() {
    override fun subscribeActual(s: Subscriber<in T>) {
        launch(Unconfined) {
            val parent = ProduceSubscription(s, coroutineContext)
            s.onSubscribe(parent)
            producer(parent)
        }
    }
}

open class ProduceSubscription<T> : Subscription, SuspendEmitter<T> {

    companion object {
        val CANCELLED = Object()
    }

    @Suppress("DEPRECATION")
    override val context: CoroutineContext
        get() = ctx

    override val isActive: Boolean
        get() = job.get() != CANCELLED


    private val actual: Subscriber<in T>

    private val ctx : CoroutineContext

    private val job = AtomicReference<Any>()

    private val requested = AtomicLong()

    private val resume = AtomicReference<Cont?>()

    private var done: Boolean = false

    constructor(
            actual: Subscriber<in T>,
            ctx: CoroutineContext) {
        this.actual = actual
        this.ctx = ctx
        setJob(ctx[Job])
    }

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
        if (job.get() == CANCELLED) {
            suspendCoroutine<Unit> { }
        }
    }

    override suspend fun onComplete() {
        if (!done) {
            done = true
            actual.onComplete()
            cancel()
        }
        if (job.get() == CANCELLED) {
            suspendCoroutine<Unit> { }
        }
    }

    override fun cancel() {
        val o = job.getAndSet(CANCELLED)
        if (o != CANCELLED) {
            (o as Job).cancel()
        }
    }

    override fun request(n: Long) {
        if (BackpressureHelper.add(requested, n) == 0L) {
            notify(resume)
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

    override fun isCancelled(): Boolean {
        return job.get() == CANCELLED
    }
}

class ProduceWithResource<T>(actual: Subscriber<in T>,
                             ctx: CoroutineContext) : ProduceSubscription<T>(actual, ctx) {
    private val resource = AtomicReference<Subscription>()

    fun setResource(s: Subscription) {
        SubscriptionHelper.replace(resource, s)
    }

    override fun cancel() {
        SubscriptionHelper.cancel(resource)
        super.cancel()
    }
}