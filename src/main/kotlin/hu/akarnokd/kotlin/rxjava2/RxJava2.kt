package hu.akarnokd.kotlin.rxjava2

import io.reactivex.Flowable
import io.reactivex.internal.operators.flowable.*
import io.reactivex.Scheduler
import org.reactivestreams.Publisher

/**
 * Convert a Publisher into a Flowable.
 */
fun <T> Publisher<T>.toFlowable() : Flowable<T> {
    return Flowable.fromPublisher(this);
}

/**
 * Observe the onNext, onError and onComplete events on the specified scheduler.
 */
fun <T> Publisher<T>.observeOn(scheduler: Scheduler, delayError: Boolean = false, prefetch : Int = 128) : Flowable<T> {
    return FlowableObserveOn(this, scheduler, delayError, prefetch)
}