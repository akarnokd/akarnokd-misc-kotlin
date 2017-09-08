package hu.akarnokd.kotlin.scrabble

import kotlinx.coroutines.experimental.runBlocking
import java.nio.file.Files
import java.nio.file.Paths
import java.util.HashSet
import hu.akarnokd.kotlin.*
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.HashMap
import java.util.Comparator
import java.util.TreeMap

fun main(arg: Array<String>) = runBlocking<Unit> {
    val state = ScrabbleState()

    bench("Direct") {
        val treemap = TreeMap<Int, MutableList<String>>(Comparator.reverseOrder())

        for (word in state.shakespeareWords) {
            if (state.scrabbleWords.contains(word)) {
                val wordHistogram = LinkedHashMap<Int, MutableLong>()
                for (i in 0 until word.length) {
                    var newValue = wordHistogram.get(word[i].toInt())
                    if (newValue == null) {
                        newValue = MutableLong()
                        wordHistogram.put(word[i].toInt(), newValue)
                    }
                    newValue.incAndSet()
                }
                var sum = 0L
                for (entry in wordHistogram.entries) {
                    sum += Math.max(0L, entry.value.get() -
                            state.scrabbleAvailableLetters[entry.key - 'a'.toInt()])
                }
                val b = sum <= 2L

                if (b) {
                    // redo the histogram?!
    //                    wordHistogram = new HashMap<>();
    //                    for (int i = 0; i < word.length(); i++) {
    //                        MutableLong newValue = wordHistogram.get((int)word.charAt(i)) ;
    //                        if (newValue == null) {
    //                            newValue = new MutableLong();
    //                            wordHistogram.put((int)word.charAt(i), newValue);
    //                        }
    //                        newValue.incAndSet();
    //                    }

                    var sum2 = 0
                    for (entry in wordHistogram.entries) {
                        sum2 += state.letterScores[entry.key - 'a'.toInt()] *
                                Math.min(
                                        entry.value.get().toInt(),
                                        state.scrabbleAvailableLetters[entry.key - 'a'.toInt()]
                                )
                    }
                    var max2 = 0
                    for (i in 0 until Math.min(3, word.length)) {
                        max2 = Math.max(max2, state.letterScores[word[i] - 'a'])
                    }

                    for (i in 3 until word.length) {
                        max2 = Math.max(max2, state.letterScores[word[i] - 'a'])
                    }

                    sum2 += max2
                    sum2 = 2 * sum2 + (if (word.length == 7) 50 else 0)

                    val key = sum2

                    var list = treemap.get(key)
                    if (list == null) {
                        list = ArrayList<String>()
                        treemap.put(key, list)
                    }
                    list.add(word)
                }
            }
        }

        val list = ArrayList<MutableMap.MutableEntry<Int, MutableList<String>>>()

        var i = 4
        for (e in treemap.entries) {
            if (--i == 0) {
                break
            }
            list.add(e)
        }

        list
    }
}

suspend fun bench(name: String, task: suspend () -> Any?) {

    val list = ArrayList<Double>()
    for (i in 1..500) {
        val before = System.nanoTime()
        task()
        val after = System.nanoTime()
        val speed = (after - before) / 1000000.0
        //System.out.printf("%3d: %.2f%n", i, speed);
        list.add(speed)
    }

    list.sort()

    System.out.printf("%s, ----- %.6f ms%n", name, list[list.size / 2])
}

class ScrabbleState {

    val scrabbleWords: Collection<String>

    val shakespeareWords: Collection<String>

    val letterScores = intArrayOf(
            // a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p,  q, r, s, t, u, v, w, x, y,  z
            1, 3, 3, 2, 1, 4, 2, 4, 1, 8, 5, 1, 3, 1, 1, 3, 10, 1, 1, 1, 1, 4, 4, 8, 4, 10)

    val scrabbleAvailableLetters = intArrayOf(
            // a, b, c, d,  e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
            9, 2, 2, 1, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1)

    @Volatile
    var result: Any? = null

    val letter_a = 'a'.toInt()

    constructor() {
        scrabbleWords = readWords("files/ospd.txt", HashSet())
        shakespeareWords = readWords("files/words.shakespeare.txt", HashSet())
    }

    private fun <E : MutableCollection<String>> readWords(name: String, coll: E): E {
        val lines = Files.readAllLines(Paths.get(name))

        for (item in lines) {
            coll.add(item.toLowerCase())
        }

        return coll
    }
}

internal class MutableLong {
    var value: Long = 0
    fun get(): Long {
        return value
    }

    fun set(l: Long): MutableLong {
        value = l
        return this
    }

    fun incAndSet(): MutableLong {
        value++
        return this
    }

    fun add(other: MutableLong): MutableLong {
        value += other.value
        return this
    }
}