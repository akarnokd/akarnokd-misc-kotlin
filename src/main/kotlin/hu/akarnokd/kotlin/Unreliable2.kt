package hu.akarnokd.kotlin

    import hu.akarnokd.rxjava2.expr.StatementObservable
    import io.reactivex.Observable
    import io.reactivex.functions.BooleanSupplier
    import io.reactivex.subjects.PublishSubject
    import java.util.concurrent.Callable
    import java.util.concurrent.ConcurrentLinkedQueue
    import java.util.concurrent.ThreadLocalRandom

    var counter = 0;

    fun service() : Observable<String> {
        return Observable.defer(Callable {
            val n = ThreadLocalRandom.current().nextInt(21)
            val c = counter++;
            Observable.range(1, n).map({ v -> "" + c + " | " + v })
        })
    }

    fun getPage(pageSignal : Observable<Int>, pageSize: Int) : Observable<List<String>> {
        return Observable.defer(Callable {
            val queue = ConcurrentLinkedQueue<String>()

            pageSignal.concatMap({ _ ->
                StatementObservable.whileDo(
                        service()
                                .toList()
                                .doOnSuccess({ v -> v.forEach { queue.offer(it) }})
                                .toObservable()
                        , BooleanSupplier { queue.size < pageSize })
                        .ignoreElements()
                        .andThen(
                                Observable.range(1, pageSize)
                                        .concatMap({ _ ->
                                            val o = queue.poll();
                                            if (o == null) {
                                                Observable.empty()
                                            } else {
                                                Observable.just(o)
                                            }
                                        })
                                        .toList()
                                        .toObservable()
                        )
            })
        })
    }

    fun main(args: Array<String>) {

        val pages = PublishSubject.create<Int>();

        getPage(pages, 20)
                .subscribe({ println(it) }, { it.printStackTrace() })

        pages.onNext(1)

        pages.onNext(2)
    }