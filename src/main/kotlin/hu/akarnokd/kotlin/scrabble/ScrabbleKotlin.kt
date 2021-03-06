package hu.akarnokd.kotlin.scrabble

import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

fun main(args: Array<String>) {
    println("Hello world!");

    benchmark(0);
    benchmark(1);
    benchmark(2);
}

fun benchmark(mode: Int) {
    val scrabble = Scrabble();

    if (mode == 0) {
        println(scrabble.run(false))
    } else
    if (mode == 1) {
        println(scrabble.run(true))
    } else {
        println(scrabble.run2())
    }

    val list = ArrayList<Double>();
    for (i in 1..500) {
        val before = System.nanoTime();
        if (mode == 0) {
            scrabble.run(false)
        } else
        if (mode == 1) {
            scrabble.run(true)
        } else {
            scrabble.run2()
        }
        val after = System.nanoTime();
        val speed = (after - before) / 1000000.0;
        //System.out.printf("%3d: %.2f%n", i, speed);
        list.add(speed);
    }

    list.sort();

    if (mode == 0) {
        print("Simple ")
    } else
    if (mode == 1) {
        print("Double ")
    } else {
        print("Alt    ")
    }

    System.out.printf("----- %.2f ms%n", list[list.size / 2]);
}

class Scrabble {

    val scrabbleWords : Collection<String>;

    val shakespeareWords : Collection<String>;

    val letterScores = intArrayOf(
            // a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p,  q, r, s, t, u, v, w, x, y,  z
               1, 3, 3, 2, 1, 4, 2, 4, 1, 8, 5, 1, 3, 1, 1, 3, 10, 1, 1, 1, 1, 4, 4, 8, 4, 10)

    val scrabbleAvailableLetters = intArrayOf(
            // a, b, c, d,  e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
               9, 2, 2, 1, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1)

    @Volatile var result : Any? = null;

    val letter_a = 'a'.toInt()

    constructor() {
        scrabbleWords = readWords("files/ospd.txt", HashSet())
        shakespeareWords = readWords("files/words.shakespeare.txt", HashSet())
    }

    private fun <E : MutableCollection<String>> readWords(name: String, coll: E) : E {
        val lines = Files.readAllLines(Paths.get(name))

        for (item in lines) {
            coll.add(item.toLowerCase());
        }

        return coll
    }

    fun run(doubleStream : Boolean) : Any? {

        val scoreOfALetter : (Int) -> Int = { letter -> letterScores[letter - 'a'.toInt()] }

        val letterScore : (Map.Entry<Int, Long>) -> Int = { entry ->
            letterScores[entry.key - 'a'.toInt()] *
                    Math.min(entry.value.toInt(), scrabbleAvailableLetters[entry.key - 'a'.toInt()])
        }

        val histoOfLetters : (String) -> Map<Int, Long> = { word ->
            /*
            val histomap = HashMap<Int, Long>();

            for (i in 0..(word.length - 1)) {
                histomap.compute(word[i].toInt(), { k, v -> if (v == null) 1 else v + 1 })

            }

            histomap
            */

            word.asSequence()
                    .groupBy(Char::toInt)
                    .mapValues { it.value.sumBy { 1 }.toLong() }
        }

        val blank : (Map.Entry<Int, Long>) -> Long = {
            entry -> Math.max(0, entry.value - scrabbleAvailableLetters[entry.key - 'a'.toInt()])
        }

        val nBlanks : (String) -> Long = { word ->
            histoOfLetters(word)
                    .map(blank)
                    .sum()
        }

        val checkBlanks : (String) -> Boolean = {
            word -> nBlanks(word) <= 2
        }

        val score2 : (String) -> Int = {
            word -> histoOfLetters(word)
                .map(letterScore)
                .sum()
        }

        val first3 : (String) -> Sequence<Int> = { word ->
            word.asSequence().take(3).map(Char::toInt)
        }

        val last3 : (String) -> Sequence<Int> = { word ->
            word.asSequence().drop(Math.max(0, word.length - 4)).map(Char::toInt)
        }

        val toBeMaxed : (String) -> Sequence<Int> = { word ->
            sequenceOf(first3(word), last3(word)).flatMap({v -> v})
        }

        val bonusForDoubleLetter : (String) -> Int = { word ->
            toBeMaxed(word)
                    .map(scoreOfALetter)
                    .max() ?: 0
        }

        val score3 : (String) -> Int =
        if (doubleStream) {
            { word ->
                (score2(word)) + (score2(word)) +
                        (bonusForDoubleLetter(word)) + (bonusForDoubleLetter(word)) +
                        (if (word.length == 7) 50 else 0)
            }

        } else {
            { word ->
                (2 * score2(word)) + (2 * bonusForDoubleLetter(word)) +
                        (if (word.length == 7) 50 else 0)
            }
        }

        val buildHistoOfLetters : ((String) -> Int) -> Map<Int, List<String>> = { score ->
            shakespeareWords
                    .filter { word -> scrabbleWords.contains(word) }
                    .filter(checkBlanks)
                    .groupByTo(TreeMap(Comparator.reverseOrder()), score)
        }

        val finalList = buildHistoOfLetters(score3)
                .entries
                .take(3)

        /*
        val finalList = sequenceOf("jezebel")
                .filter { word -> scrabbleWords.contains(word) }
                .filter(checkBlanks)
                .groupByTo(TreeMap(Comparator.reverseOrder()), score3)
        */

        // finalList.forEach { println(it) }

        result = finalList

        return finalList;
    }

    fun run2(): Any?  {

        val scoreOfALetter: (Char) -> Int = { letter ->
            letterScores[letter.toInt() - letter_a]
        }

        val letterScore: (Map.Entry<Int, Long>) -> Int = { entry ->
            letterScores[entry.key - letter_a] *
                    Math.min(entry.value.toInt(), scrabbleAvailableLetters[entry.key  - letter_a])
        }

        fun histoOfLetters(word: String): Map<Int, Long> = word.groupBy { it.toInt() }.mapValues { it.value.size.toLong() }

        fun blank(entry: Map.Entry<Int, Long>): Long =
                Math.max(0L, entry.value - scrabbleAvailableLetters[entry.key - letter_a])

        fun nBlanks(word: String): Long = histoOfLetters(word).entries.map { blank(it) }.sum()

        fun checkBlanks(word: String) = nBlanks(word) <= 2

        fun score2(word: String): Int = histoOfLetters(word).entries.map(letterScore).sum()

        fun first3(word: String): List<Char> = word.toCharArray().take(3)

        fun last3(word: String): List<Char> = word.toCharArray().takeLast(3)

        fun toBeMaxed(word: String): Sequence<Char> = sequenceOf(first3(word), last3(word)).flatten()

        fun bonusForDoubleLetter(word: String): Int = toBeMaxed(word).map { scoreOfALetter(it) }.max()?:0

        fun score3(word: String): Int = 2 * (score2(word) + bonusForDoubleLetter(word)) + if (word.length == 7) 50 else 0

        fun buildHistoOnScore(score: (String) -> Int): Map<Int, List<String>> =
                shakespeareWords
                        .filter { scrabbleWords.contains(it) }
                        .filter { checkBlanks(it) }
                        .groupByTo(TreeMap<Int, MutableList<String>>(Collections.reverseOrder()), score)

        return buildHistoOnScore(::score3).entries.take(3)

    }
}