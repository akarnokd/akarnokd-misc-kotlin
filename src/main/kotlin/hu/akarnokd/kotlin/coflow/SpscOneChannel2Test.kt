package hu.akarnokd.kotlin.coflow

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

object SpscOneChannel2Test {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        val queue = SpscOneChannel<Any>()

        launch(CommonPool) {
            queue.send(0)
            queue.send(1)
        }

        println(queue.receive())
        println(queue.receive())
    }
}