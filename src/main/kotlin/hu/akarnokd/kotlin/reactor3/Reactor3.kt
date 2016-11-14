package hu.akarnokd.kotlin.reactor3

import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

fun <T> Publisher<T>.toFlux() : Flux<T> {
    return Flux.from(this)
}