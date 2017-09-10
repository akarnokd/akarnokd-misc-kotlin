package hu.akarnokd.kotlin.coflow

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.coroutines.experimental.suspendCoroutine

class SpscArrayChannel<T>(private val capacity: Int) : AtomicReferenceArray<T>(capacity) {

    private val empty = AtomicReference<Cont?>()

    private val full = AtomicReference<Cont?>()

    private val producerIndex = AtomicLong()

    private val consumerIndex = AtomicLong()

    suspend fun send(t: T) {
        val producerIndex = this.producerIndex
        val pi = producerIndex.get()
        val cap = capacity;
        val offset = pi.toInt() and (cap - 1)

        val consumerIndex = this.consumerIndex

        while (true) {

            val ci = consumerIndex.get()

            if (ci + cap == pi) {
                suspendCoroutine<Unit> { cont -> await(full, cont) }
            } else {
                lazySet(offset, t)
                producerIndex.set(pi + 1)
                if (pi == consumerIndex.get()) {
                    notify(empty)
                }
                break;
            }
        }
    }

    suspend fun receive() : T {
        val consumerIndex = this.consumerIndex

        val ci = consumerIndex.get()
        val cap = capacity;
        val offset = ci.toInt() and (cap - 1)
        val producerIndex = this.producerIndex

        while (true) {
            val pi = producerIndex.get()

            if (ci == pi) {
                suspendCoroutine<Unit> { cont -> await(empty, cont) }
            } else {
                val v = get(offset)
                lazySet(offset, null)
                consumerIndex.set(ci + 1)
                if (ci + cap == producerIndex.get()) {
                    notify(full)
                }
                return v
            }
        }
    }
}