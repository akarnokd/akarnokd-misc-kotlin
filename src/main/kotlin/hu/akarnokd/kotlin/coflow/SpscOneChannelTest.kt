package hu.akarnokd.kotlin.coflow

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

object SpscOneChannelTest {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        val complete = Object()
        val queue = SpscOneChannel<Any>()

        launch(CommonPool) {
            for (i in 0 until 1_000_000) {
                if (i % 10_000 == 0) {
                    println("Sent: $i")
                }
                queue.send(i)
            }
            queue.send(complete)
        }

        var j = 0
        while (true) {
            val o = queue.receive()
            if (o == complete) {
                break
            }
            if (o !is Int) {
                throw IllegalArgumentException("" + o.javaClass)
            }
            if (j % 10_000 == 0) {
                println("Received: $j")
            }
            if (o != j) {
                throw IllegalArgumentException("Wrong value $o <-> $j")
            }
            j++
        }

        println(j)
    }
}