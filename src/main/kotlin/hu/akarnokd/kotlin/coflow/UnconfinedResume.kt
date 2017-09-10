package hu.akarnokd.kotlin.coflow

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

object UnconfinedResume {
    @JvmStatic
    fun main(args: Array<String>) {
        repeat(2) {
            launch(Unconfined) {
                println("A")
                launch(CommonPool) {
                    println("B")
                    delay(100)
                    println("C")
                }
                        .join()
                println("D")
            }
        }
        Thread.sleep(1000)
    }
}