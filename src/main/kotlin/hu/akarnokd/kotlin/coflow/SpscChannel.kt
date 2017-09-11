package hu.akarnokd.kotlin.coflow

import io.reactivex.internal.queue.SpscArrayQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

typealias Cont = Continuation<Unit>

class SpscChannel<T> {

    val queue : SpscArrayQueue<T>

    val empty : AtomicReference<Cont?>
    val full : AtomicReference<Cont?>

    constructor (capacity: Int) {
        queue = SpscArrayQueue(capacity)
        empty = AtomicReference<Cont?>()
        full = AtomicReference<Cont?>()
    }

    suspend fun send(t: T) {
        while (true) {
            if (queue.offer(t)) {
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
            val v = queue.poll()
            if (v != null) {
                notify(full)
                return v;
            }
            suspendCoroutine<Unit> { cont ->
                await(empty, cont)
            }
        }
    }
}