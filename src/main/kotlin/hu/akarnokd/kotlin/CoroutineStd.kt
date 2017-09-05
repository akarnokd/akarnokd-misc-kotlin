package hu.akarnokd.kotlin

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

fun main(arg: Array<String>) = runBlocking<Unit> {

    val a = A();
    val n = 1_000_000
    for (j in 0 until 15) {

        val t0 = System.nanoTime();

        var f = launch(CommonPool) {
            for (i in 0 until n) {
                f(a, i);
            }
        }
        f.join()

        val t1 = System.nanoTime();
        println("" + j + "-" + ((n * 1E9 / (t1 - t0)).toInt()) + " ops/s")
    }
}

suspend fun f(a: A, i: Int) {
    a.obj = i
}