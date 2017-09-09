package hu.akarnokd.kotlin.scrabble

import hu.akarnokd.kotlin.coflow.*
import kotlinx.coroutines.experimental.runBlocking
import java.util.ArrayList
import java.util.TreeMap
import java.util.Comparator
import java.util.HashMap


fun main(arg: Array<String>) = runBlocking<Unit> {
    val state = ScrabbleState()

    println("Sanity check:")
    println(coFlowScrabble(state))

    bench("CoFlow") {
        coFlowScrabble(state)
    }
}

suspend fun coFlowScrabble(state: ScrabbleState) : Any? {
    //  to compute the score of a given word
    val scoreOfALetter : suspend (Int) -> Int = { letter -> state.letterScores[letter - 'a'.toInt()] }

    // score of the same letters in a word
    val letterScore : suspend (MutableMap.MutableEntry<Int, MutableLong>) -> Int = { entry ->
        state.letterScores[entry.key - 'a'.toInt()] * Math.min(
                entry.value.get().toInt(),
                state.scrabbleAvailableLetters[entry.key - 'a'.toInt()]
        )
    }


    val toIntegerFlowable : suspend (String) -> CoFlow<Int> = { string -> Chars(string) }

    // Histogram of the letters in a given word
    val histoOfLetters : suspend (String) -> CoFlow<HashMap<Int, MutableLong>> = { word ->
        toIntegerFlowable(word)
                .collect(
                        { HashMap<Int, MutableLong>() }

                ) { map: HashMap<Int, MutableLong>, value: Int ->
                    var newValue: MutableLong? = map[value]
                    if (newValue == null) {
                        newValue = MutableLong()
                        map.put(value, newValue)
                    }
                    newValue.incAndSet()
                }
    }

    // number of blanks for a given letter
    val blank : suspend (MutableMap.MutableEntry<Int, MutableLong>) -> Long = { entry ->
        Math.max(
                0L,
                entry.value.get() - state.scrabbleAvailableLetters[entry.key - 'a'.toInt()]
        )
    }

    // number of blanks for a given word
    val nBlanks : suspend (String) -> CoFlow<Long> = { word ->
        histoOfLetters(word).flatten { it.entries }
                .map(blank)
                .sumLong()
    }


    // can a word be written with 2 blanks?
    val checkBlanks : suspend (String) -> CoFlow<Boolean> = { word ->
        nBlanks(word)
                .map { l -> l <= 2L }
    }

    // score taking blanks into account letterScore1
    val score2 : suspend (String) -> CoFlow<Int> = { word ->
        histoOfLetters(word).flatten { it.entries }
                .map(letterScore)
                .sumInt()
    }

    // Placing the word on the board
    // Building the streams of first and last letters
    val first3 : suspend (String) -> CoFlow<Int> = { word -> Chars(word).take(3) }
    val last3 : suspend (String) -> CoFlow<Int> = { word -> Chars(word).skip(3) }


    // Stream to be maxed
    val toBeMaxed : suspend (String) -> CoFlow<Int> = { word -> concat(first3(word), last3(word)) }

    // Bonus for double letter
    val bonusForDoubleLetter : suspend (String) -> CoFlow<Int> = { word ->
        toBeMaxed(word)
                .map(scoreOfALetter)
                .max()

    }

    // score of the word put on the board
    val score3 : suspend (String) -> CoFlow<Int> = { word ->
        concat(
                score2(word),
                bonusForDoubleLetter(word)
        ).sumInt()
                .map { v -> v * 2 + (if (word.length == 7) 50 else 0) }
    }

    val buildHistoOnScore : suspend (suspend (String) -> CoFlow<Int>) -> CoFlow<TreeMap<Int, MutableList<String>>> =
            { score ->
                FromIterable(state.shakespeareWords)
                        .filter { state.scrabbleWords.contains(it) }
                        .filter { word -> checkBlanks(word).awaitFirst() }
                        .collect(
                                { TreeMap<Int, MutableList<String>>(Comparator.reverseOrder()) },
                                { map: TreeMap<Int, MutableList<String>>, word: String ->
                                    val key = score(word).awaitFirst()
                                    var list: MutableList<String>? = map[key]
                                    if (list == null) {
                                        list = ArrayList()
                                        map.put(key, list)
                                    }
                                    list.add(word)
                                }
                        )
            }

    // best key / value pairs


//        System.out.println(finalList2);

    return buildHistoOnScore(score3).flatten { it.entries }
            .take(3)
            .collect(
                    { ArrayList<MutableMap.MutableEntry<Int, MutableList<String>>>() },
                    { list, entry -> list.add(entry) }
            )
            .awaitFirst()
}