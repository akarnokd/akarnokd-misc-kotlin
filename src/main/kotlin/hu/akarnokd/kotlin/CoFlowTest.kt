package hu.akarnokd.kotlin

import kotlinx.coroutines.experimental.runBlocking

fun main(arg: Array<String>) = runBlocking<Unit> {
    println(Just(1).awaitFirst())
}