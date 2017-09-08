package hu.akarnokd.kotlin

import io.reactivex.Flowable
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.experimental.CoroutineContext

interface CoroutineEmitter<in T> {

    suspend fun next(t: T)

    suspend fun error(t: Throwable)

    suspend fun complete()
}

fun <T> Flowable<T>.coroutine(
        context: CoroutineContext,
        generator: suspend (emitter: CoroutineEmitter<T>) -> Unit) {



}

class CoroutineSubscription<T>(actual: Subscriber<in T>,
                               context: CoroutineContext,
                               generator: suspend (emitter: CoroutineEmitter<T>) -> Unit) : Subscription {

    val requested = AtomicLong()

    @Volatile
    var cancelled: Boolean = false

    override fun cancel() {
        cancelled = true;
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun request(n: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.


    }

}

