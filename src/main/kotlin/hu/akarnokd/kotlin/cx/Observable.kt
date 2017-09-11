/*
 * Copyright (C) 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.akarnokd.kotlin.cx

import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.channels.ProducerScope
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import java.util.function.Consumer
import kotlin.coroutines.experimental.CoroutineContext

abstract class Observable<T> {
    suspend abstract fun subscribe(): ReceiveChannel<T>

    @JvmOverloads
    fun subscribe(
            onNext: Consumer<T>,
            onError: Consumer<Throwable> = NoLambda,
            onComplete: Runnable = NoLambda
    ) = runBlocking(Unconfined) {
        try {
            for (value in subscribe()) {
                onNext.accept(value)
            }
            onComplete.run()
        } catch (t: Throwable) {
            if (t is Error) {
                throw t
            }
            onError.accept(t)
        }
    }

    private object NoLambda : Consumer<Throwable>, Runnable {
        override fun accept(t: Throwable) = Unit
        override fun run() = Unit
    }

    fun <O> map(mapper: suspend (T) -> O): Observable<O> = Map(this, mapper)
    fun filter(predicate: suspend (T) -> Boolean): Observable<T> = Filter(this, predicate)
    fun take(count: Int): Observable<T> = Take(this, count)
    fun skip(count: Int): Observable<T> = Skip(this, count)
    fun <R> collect(collectionSupplier: () -> R, collector: suspend (R, T) -> Unit): Observable<R> = Collect(this, collectionSupplier, collector)
    fun <O> flatten(mapper: (T) -> Iterable<O>): Observable<O> = Flatten(this, mapper)

    companion object {
        @JvmStatic
        fun <O> just(value: O): Observable<O> = FromValue(value)
        @JvmStatic
        fun <O> fromIterable(iterable: Iterable<O>): Observable<O> = FromIterable(iterable)
        @JvmStatic
        fun <T> concat(vararg sources: Observable<T>): Observable<T> = Concat(arrayOf(*sources))
    }
}

suspend fun <E> prod(coroutineContext: CoroutineContext
, block: suspend ProducerScope<E>.() -> Unit) = produce(coroutineContext, 128, block)

class Chars(private val string: String): Observable<Int>() {
    suspend override fun subscribe() = prod(Unconfined) {
        for (char in string) {
            send(char.toInt())
        }
    }
}

private class FromValue<O>(private val value: O) : Observable<O>() {
    suspend override fun subscribe() = prod(Unconfined) { send(value) }
}

private class FromIterable<O>(private val iterable: Iterable<O>): Observable<O>() {
    suspend override fun subscribe() = prod(Unconfined) {
        iterable.forEach { send(it) }
    }
}

private class Map<in U, D>(
        private val upstream: Observable<U>,
        private val mapper: suspend (U) -> D
) : Observable<D>() {
    suspend override fun subscribe() = prod(Unconfined) {
        for (value in upstream.subscribe()) {
            send(mapper(value))
        }
    }
}

private class Filter<O>(
        private val upstream: Observable<O>,
        private val predicate: suspend (O) -> Boolean
) : Observable<O>() {
    suspend override fun subscribe() = prod(Unconfined) {
        for (value in upstream.subscribe()) {
            if (predicate(value)) {
                send(value)
            }
        }
    }
}

private class Take<O>(
        private val upstream: Observable<O>,
        private val count: Int
) : Observable<O>() {
    suspend override fun subscribe() = prod(Unconfined) {
        var seen = 0
        for (value in upstream.subscribe()) {
            send(value)
            if (++seen == count) {
                break
            }
        }
    }
}

private class Skip<O>(
        private val upstream: Observable<O>,
        private val count: Int
) : Observable<O>() {
    suspend override fun subscribe() = prod(Unconfined) {
        var seen = 0
        for (value in upstream.subscribe()) {
            if (++seen > count) {
                send(value)
            }
        }
    }
}

private class Collect<T, R>(
        private val upstream: Observable<T>,
        private val collectionSupplier: () -> R,
        private val collector: suspend (R, T) -> Unit
) : Observable<R>() {
    suspend override fun subscribe() = prod(Unconfined) {
        val collection = collectionSupplier()
        for (value in upstream.subscribe()) {
            collector(collection, value)
        }
        send(collection)
    }
}

private class Flatten<in U, D>(
        private val upstream: Observable<U>,
        private val mapper: (U) -> Iterable<D>
) : Observable<D>() {
    suspend override fun subscribe() = prod(Unconfined) {
        for (value in upstream.subscribe()) {
            for (inner in mapper(value)) {
                send(inner)
            }
        }
    }
}

private class Concat<O>(private val sources: Array<Observable<O>>) : Observable<O>() {
    suspend override fun subscribe() = prod(Unconfined) {
        for (source in sources) {
            for (value in source.subscribe()) {
                send(value)
            }
        }
    }
}

fun Observable<Int>.sumInt(): Observable<Int> = SumInt(this)
fun Observable<Long>.sumLong(): Observable<Long> = SumLong(this)
fun <T : Comparable<T>> Observable<T>.max(): Observable<T> = Max(this)

class Max<T : Comparable<T>>(private val upstream: Observable<T>) : Observable<T>() {
    suspend override fun subscribe() = prod(Unconfined) {
        val values = upstream.subscribe()
        var max = values.receiveOrNull() ?: return@prod
        for (value in values) {
            if (value > max) {
                max = value
            }
        }
        send(max)
    }
}

private class SumInt(private val upstream: Observable<Int>) : Observable<Int>() {
    suspend override fun subscribe() = prod(Unconfined) {
        var sum = 0
        for (value in upstream.subscribe()) {
            sum += value
        }
        send(sum)
    }
}

private class SumLong(private val upstream: Observable<Long>) : Observable<Long>() {
    suspend override fun subscribe() = prod(Unconfined) {
        var sum = 0L
        for (value in upstream.subscribe()) {
            sum += value
        }
        send(sum)
    }
}

suspend fun <T> Observable<T>.awaitFirst() : T {
    return subscribe().receive()
}