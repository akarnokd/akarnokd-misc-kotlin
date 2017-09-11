package hu.akarnokd.kotlin

import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.*
import java.util.concurrent.TimeUnit

object RxJavaCoroutineInteropTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val f = produceFlow {
            for (i in 0 until 10) {
                println("Generating $i")
                onNext(i)
            }
            onComplete()
        }

        f.test(0)
                .assertEmpty()
                .requestMore(5)
                .assertValues(0, 1, 2, 3, 4)
                .requestMore(5)
                .assertResult(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

        f.transform({
            if (it % 2 == 0) {
                onNext(it)
            }
        })
        .test()
        .assertResult(0, 2, 4, 6, 8)

        f.transform({
            onNext(it)
            onNext(it + 1)
        })
        .test()
        .assertResult(0, 1, 1, 2, 2, 3, 3, 4, 4,
                5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10)

        f.transform({
            launch(CommonPool) {
                onNext(it + 1)
            }
        })
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        old(f)

        println("---------------------")
        println("Transform 1 - N async")
        println("---------------------")

        f.transform<Int, Int>({ t ->
            println("$t - before sync")
            onNext(t)
            println("$t - after sync")
            launch(CommonPool) {
                println("${t + 1} - sleep async")
                delay(100)
                println("${t + 1} - before async")
                onNext(t + 1)
                println("${t + 1} - after async ")
            }
            .join()
            println("$t - transformed sync")
        })
        .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertResult(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10)

        runBlocking {
            for (i in f.toReceiver()) {
                println(i)
            }
            println("Done")

            for (i in f.subscribeOn(Schedulers.single()).toReceiver()) {
                println("Async $i")
            }
            println("Async Done")
        }
    }

    fun old(f: Flowable<Int>) {

        println("---------")
        println("Unbounded")
        println("---------")

        f.test().assertResult(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

        println("-------")
        println("Take(5)")
        println("-------")

        f.take(5).test().assertResult(0, 1, 2, 3, 4)

        println("----------")
        println("One by one")
        println("----------")

        val ts = f.test(0)

        for (i in 0 until 10) {
            ts.assertValueCount(i)
            println("Req ${i + 1}")
            ts.requestMore(1)
            ts.assertValueAt(i, i)
        }
        ts.assertResult(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

        println("---------------")
        println("Transform 1 - 0")
        println("---------------")

        f.transform<Int, Int>({ t ->

        })
                .test()
                .assertResult()

        println("---------------")
        println("Transform 1 - 1")
        println("---------------")

        f.transform<Int, Int>({ t ->
            onNext(t + 1)
        })
                .test()
                .assertResult(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        println("---------------")
        println("Transform 1 - N")
        println("---------------")

        val ts1 = f.transform<Int, Int>({ t ->
            onNext(t)
            onNext(t + 1)
        })
                .test(0)

        val items = arrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10)

        for (i in 0 until items.size) {
            ts1.assertValueCount(i)
            println("Req ${i + 1}")
            ts1.requestMore(1)
            ts1.assertValueAt(i, items[i])
        }

        ts1.requestMore(1)
        ts1.assertValueCount(items.size)
        ts1.assertComplete()

    }
}