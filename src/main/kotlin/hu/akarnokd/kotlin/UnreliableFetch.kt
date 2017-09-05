package hu.akarnokd.kotlin

    import io.reactivex.Flowable
    import io.reactivex.Observable
    import io.reactivex.Single
    import io.reactivex.functions.Consumer
    import io.reactivex.subjects.PublishSubject
    import java.util.concurrent.ThreadLocalRandom
    import java.util.concurrent.atomic.AtomicInteger

    fun service(page: Int, size: Int) : Observable<String> {
        if (ThreadLocalRandom.current().nextDouble() < 0.7) {
            val n = ThreadLocalRandom.current().nextInt(size + 1)
            return Observable.range(1, n).map({ "" + page + " - " + it})
        }
        return Observable.range(1, size).map({ "" + page + " - " + it})
    }

    fun fetchPages(pageIndex: Observable<Int>, size: Int) : Observable<MutableList<String>> {
        return pageIndex
                .concatMap({ page ->
                    val counter = AtomicInteger()

                    val w: Flowable<MutableList<String>> = Single.defer({
                        service(page, size)
                                .skip(counter.get().toLong())
                                .toList()
                                .doOnSuccess(Consumer { v -> counter.addAndGet(v.size) })
                    }).repeatUntil({ counter.get() == size })

                    w.flatMapIterable { v -> v }
                            .toList()
                            .toObservable()
                })
    }

    fun main(args: Array<String>) {
        val page = PublishSubject.create<Int>()

        fetchPages(page, 20)
                .subscribe{ println(it) }

        page.onNext(1)

        page.onNext(2)

    }