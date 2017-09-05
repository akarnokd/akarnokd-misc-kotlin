package hu.akarnokd.kotlin

import hu.akarnokd.kotlin.scrabble.Scrabble
import io.reactivex.Observable
import ix.Ix
import org.openjdk.jmh.annotations.Benchmark
import java.util.ArrayList

fun benchmark(name: String, task: () -> Unit) {

    val list = ArrayList<Double>();
    for (i in 1..500) {
        val before = System.nanoTime();
        task()
        val after = System.nanoTime();
        val speed = (after - before) / 1000000.0;
        //System.out.printf("%3d: %.2f%n", i, speed);
        list.add(speed);
    }

    list.sort();

    System.out.printf("%s, ----- %.6f ms%n", name, list[list.size / 2]);
}

fun main(args: Array<String>) {

    benchmark("list_rxjava") {
        Observable.range(0, 1_000_000)
                .map { it + 1 }
                .blockingLast()
    }

    benchmark("list_kotlin") {
        (1..1_000_000)
                .map { it + 1 }
                .last()
    }

    benchmark("list_seq") {
        (1..1_000_000)
                .asSequence()
                .map { it + 1 }
                .last()
    }

    benchmark("multimapping_rxjava") {
        Observable.range(0, 1_000_000)
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .blockingLast()
    }

    benchmark("multimapping_kotlin") {
        (1..1_000_000)
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .last()
    }

    benchmark("multimapping_seq") {
        (1..1_000_000)
                .asSequence()
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .last()

    }

    benchmark("first_rxjava") {
        Observable.range(0, 1_000_000)
                .map { it + 1 }
                .blockingFirst()

    }

    benchmark("first_kotlin") {
        (1..1_000_000)
                .map { it + 1 }
                .first()
    }

    benchmark("first_seq") {
        (1..1_000_000)
                .asSequence()
                .map { it + 1 }
                .first()
    }

    benchmark("list_ixjava") {
        Ix.range(0, 1_000_000)
                .map { it + 1 }
                .last()
    }

    benchmark("multimapping_ixjava") {
        Ix.range(0, 1_000_000)
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .map { it + 1 }
                .last()
    }

    benchmark("first_ixjava") {
        Ix.range(0, 1_000_000)
                .map { it + 1 }
                .first()

    }
}