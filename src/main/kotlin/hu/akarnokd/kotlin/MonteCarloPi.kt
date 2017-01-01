package hu.akarnokd.kotlin

import java.util.*
import java.util.concurrent.ThreadLocalRandom

fun main(args: Array<String>) {

    val start = System.nanoTime();

    generateSequence { ThreadLocalRandom.current().nextDouble() }
            .buffer(2)
            .map({ list -> Point(list.get(0), list.get(1)) })
            // originally: fan-in, fan-out
            .map({ p ->
                if (p.isInner()) {
                    Sample.InnerSample
                } else {
                    Sample.OuterSample
                }
            })
            .scan(State(0, 0), { state, sample -> state.withSample(sample) })
            .drop(1)
            .every(1000000)
            .take(50)
            .forEach { v -> System.out.println(v) }

    val end = (System.nanoTime() - start) / 1000000.0;

    System.out.printf("Done. %.3f ms total, %.3f M samples/sec%n", end, 50000 / end)

}

class Point(val x: Double, val y: Double) {
    fun isInner() = x * x + y * y < 1;
    override fun toString(): String = "x=" + x + ", y=" + y
}

class State(var count: Int, var inner: Int) {

    override fun toString(): String {
        return "count=" + count + ", inner=" + inner + ", pi=" + 4.0 * inner / count
    }

    fun withSample(s: Sample) : State {
        return if (s == Sample.InnerSample) {
            State(count + 1, inner + 1)
        } else {
            State(count + 1, inner)
        }
    }
}

enum class Sample {
    InnerSample,
    OuterSample
}