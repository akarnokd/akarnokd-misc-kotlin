package hu.akarnokd.kotlin.coflow

import io.reactivex.internal.queue.SpscArrayQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine


class SpscOneChannel<T> {

    val queue : AtomicReference<T>

    val empty : AtomicReference<Cont?>
    val full : AtomicReference<Cont?>

    constructor() {
        queue = AtomicReference<T>()
        empty = AtomicReference<Cont?>()
        full = AtomicReference<Cont?>()
    }

    suspend fun send(t: T) {
        while (true) {
            if (queue.get() == null) {
                queue.lazySet(t);
                notify(empty)
                break;
            }
            suspendCoroutine<Unit> { cont ->
                await(full, cont)
            }
        }
    }

    suspend fun receive() : T {
        while (true) {
            val v = queue.get()
            if (v != null) {
                queue.lazySet(null)
                notify(full)
                return v;
            }
            suspendCoroutine<Unit> { cont ->
                await(empty, cont)
            }
        }
    }
}


fun notify(ref: AtomicReference<Cont?>) {
    while (true) {
        val cont = ref.get()
        val next : Cont?
        if (cont != null && cont != TOKEN) {
            cont.resume(Unit)
            next = null
        } else {
            next = TOKEN
        }
        if (ref.compareAndSet(cont, next)) {
            break;
        }
    }
}

fun await(ref: AtomicReference<Cont?>, cont: Continuation<Unit>) {
    while (true) {
        val a = ref.get()
        val b : Cont?
        if (a == TOKEN) {
            cont.resume(Unit)
            b = null
        } else {
            b = cont
        }
        if (ref.compareAndSet(a, b)) {
            break;
        }
    }
}

val TOKEN: Cont = object: Continuation<Unit> {
    override val context: CoroutineContext
        get() = throw UnsupportedOperationException()

    override fun resume(value: Unit) {
        throw UnsupportedOperationException()
    }

    override fun resumeWithException(exception: Throwable) {
        throw UnsupportedOperationException()
    }

}