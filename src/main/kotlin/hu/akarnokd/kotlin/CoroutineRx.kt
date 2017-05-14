package hu.akarnokd.kotlin

import io.reactivex.Flowable
import io.reactivex.Observable
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.reactive.consumeEach
import kotlinx.coroutines.experimental.reactive.publish
import kotlin.coroutines.experimental.CoroutineContext


fun range(context: CoroutineContext, start: Int, count : Int) = publish<Int>(context) {
    for (x in start until start + count) send(x);
}

class A {
    @Volatile var obj : Object? = null;
}

fun main(arg: Array<String>) = runBlocking<Unit> {

    println("Coroutines")
    for (i in 0 until 15) {
        val t0 = System.nanoTime();
        val n = 1000000;
        var a = A();

        range(Unconfined, 1, n).consumeEach { a.obj = it as Object }

        val t1 = System.nanoTime();
        println("" + i + "-" + ((n * 1E9 / (t1 - t0)).toInt()) + " ops/s")
    }

    println("RxJava")
    for (i in 0 until 15) {
        val t0 = System.nanoTime();
        val n = 1000000;
        var a = A();

        Flowable.range(1, n).subscribe { a.obj = it as Object }

        val t1 = System.nanoTime();
        println("" + i + "-" + ((n * 1E9 / (t1 - t0)).toInt()) + " ops/s")
    }


    println("RxJava - Observable")
    for (i in 0 until 15) {
        val t0 = System.nanoTime();
        val n = 1000000;
        var a = A();

        Observable.range(1, n).subscribe { a.obj = it as Object }

        val t1 = System.nanoTime();
        println("" + i + "-" + ((n * 1E9 / (t1 - t0)).toInt()) + " ops/s")
    }

}
