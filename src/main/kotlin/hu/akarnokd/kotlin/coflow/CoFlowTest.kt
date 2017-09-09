package hu.akarnokd.kotlin.coflow

import hu.akarnokd.kotlin.coflow.Just
import hu.akarnokd.kotlin.coflow.awaitFirst
import kotlinx.coroutines.experimental.runBlocking

fun main(arg: Array<String>) = runBlocking<Unit> {
    println(Just(1).awaitFirst())
}