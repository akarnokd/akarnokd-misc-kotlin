package hu.akarnokd.kotlin

import io.reactivex.Observable
import io.reactivex.ObservableTransformer

/**
 * Created by akarnokd on 2017.01.27..
 */

fun main(args: Array<String>) {

    val a = Observable.just(1);

    val o = a.compose(mytransform<Int>()).map({ u -> u + 1 })

    o.blockingFirst()
}

fun <T> mytransform() : ObservableTransformer<T, T> {
    return ObservableTransformer {
        it
    }
}