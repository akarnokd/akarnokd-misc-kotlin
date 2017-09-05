package hu.akarnokd.kotlin

import io.reactivex.Observable
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder



/**
 * gradle jmh -Pjmh='ListBenchJmh'
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
@State(Scope.Thread)
open class ListBenchJmh {

    @Benchmark
    fun list_rxjava() {
        Observable.range(0, 1_000_000)
                .map { it + 1 }
                .blockingLast()
    }

    @Benchmark
    fun list_kotlin() {
        (1..1_000_000)
                .map { it + 1 }
                .last()
    }

    @Benchmark
    fun list_seq() {
        (1..1_000_000)
                .asSequence()
                .map { it + 1 }
                .last()
    }

    @Benchmark
    fun multimapping_rxjava() {
        Observable.range(0, 1_000_000)
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .blockingLast()
    }

    @Benchmark
    fun multimapping_kotlin() {
        (1..1_000_000)
        .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .last()
    }

    @Benchmark
    fun multimapping_seq() {
        (1..1_000_000)
                .asSequence()
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .last()
    }

    @Benchmark
    fun first_rxjava() {
        Observable.range(0, 1_000_000)
                .map { it + 1 }
                .blockingFirst()
    }

    @Benchmark
    fun first_kotlin() {
        (1..1_000_000)
                .map { it + 1 }
                .first()
    }

    @Benchmark
    fun first_seq() {
        (1..1_000_000)
                .asSequence()
                .map { it + 1 }
                .first()
    }
}

fun main(args: Array<String>) {
    val opt = OptionsBuilder()
            .include(ListBenchJmh::class.java!!.getSimpleName())
            .forks(1)
            .build()

    Runner(opt).run()
}