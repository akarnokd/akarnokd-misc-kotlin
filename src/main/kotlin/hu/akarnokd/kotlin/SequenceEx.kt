package hu.akarnokd.kotlin

import java.util.*

fun <T> Sequence<T>.buffer(n: Int) : Sequence<List<T>> {
    return BufferSequence<T>(this, n);
}

private class BufferSequence<out T>(val source: Sequence<T>, val n: Int) : Sequence<List<T>> {
    override fun iterator(): Iterator<List<T>> {
        return BufferIterator<T>(source.iterator(), n)
    }

    class BufferIterator<T>(val source: Iterator<T>, val n : Int) : Iterator<List<T>> {

        var list : ArrayList<T>? = null;

        var done: Boolean = false;

        override fun hasNext(): Boolean {
            var lst = list;
            if (lst == null) {
                if (done) {
                    return false;
                }
                lst = ArrayList<T>();
                list = lst;

                var i = n;
                while (i > 0 && source.hasNext()) {
                    lst.add(source.next());
                    i--;
                }

                if (i != 0) {
                    done = true;
                }
                return i != n;
            }
            return true;
        }

        override fun next(): List<T> {
            if (hasNext()) {
                val lst = list;
                list = null;
                if (lst != null) {
                    return lst;
                }
            }
            throw NoSuchElementException();
        }

    }
}

fun <T> Sequence<T>.every(n: Int) : Sequence<T> {
    return SequenceEvery<T>(this, n)
}

private class SequenceEvery<out T>(val source: Sequence<T>, val n: Int) : Sequence<T> {
    override fun iterator(): Iterator<T> {
        return EveryIterator<T>(source.iterator(), n)
    }

    class EveryIterator<out T>(val source: Iterator<T>, val n: Int) : Iterator<T> {
        var done: Boolean = false

        var hasValue: Boolean = false;

        override fun hasNext(): Boolean {
            if (!done) {
                if (!hasValue) {
                    var i = n - 1;

                    while (i > 0 && source.hasNext()) {
                        source.next();
                        i--;
                    }

                    if (i != 0) {
                        done = true;
                        return false;
                    }

                    if (!source.hasNext()) {
                        done = true;
                        return false;
                    }
                    hasValue = true;
                }
                return true;
            }
            return false;
        }

        override fun next(): T {
            if (hasNext()) {
                hasValue = false;
                return source.next()
            }
            throw NoSuchElementException()
        }

    }

}

fun <T, S> Sequence<T>.scan(initial: S, scanner: (S, T) -> S) : Sequence<S> {
    return ScanSequence<T, S>(this, initial, scanner)
}

private class ScanSequence<T, S>(val source: Sequence<T>, val initial: S, val scanner: (S, T) -> S) : Sequence<S> {
    override fun iterator(): Iterator<S> {
        return ScanIterator<T, S>(source.iterator(), initial, scanner)
    }

    class ScanIterator<T, S>(val source: Iterator<T>, var current: S, val scanner: (S, T) -> S) : Iterator<S> {
        var done : Boolean = false
        var hasValue : Boolean = true

        override fun hasNext(): Boolean {
            if (!done) {
                if (!hasValue) {

                    if (source.hasNext()) {
                        var c = current;

                        current = scanner(c, source.next())
                        hasValue = true;
                        return true
                    }

                    done = true
                    return false
                }
                return true;
            }
            return false;
        }

        override fun next(): S {
            if (hasNext()) {
                hasValue = false;
                return current;
            }
            throw NoSuchElementException()
        }

    }
}