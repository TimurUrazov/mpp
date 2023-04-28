package dijkstra

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import java.util.concurrent.Phaser
import kotlin.Comparator
import kotlin.concurrent.thread
import java.util.concurrent.ThreadLocalRandom

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiQueuePQ(workers, NODE_DISTANCE_COMPARATOR)
    q.add(start)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    repeat(workers) {
        thread {
            while (true) {
                val cur = q.poll()
                if (cur == null) {
                    if (q.isEmpty()) break else continue
                }
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val prev = e.to.distance
                        val current = cur.distance + e.weight
                        if (prev > current) {
                            if (e.to.casDistance(prev, current)) {
                                q.add(e.to)
                                break
                            }
                        } else {
                            break
                        }
                    }
                }
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class MultiQueuePQ<T>(private val workers: Int, private val comparator: Comparator<T>) {
    private val queues = Array(4 * workers) { PriorityQueue(comparator) }
    private val locks = Array(4 * workers) { ReentrantLock() }
    private val emptyQueues = atomic(queues.size)

    private fun nextRand(): Int {
        return ThreadLocalRandom.current().nextInt(queues.size)
    }

    fun isEmpty(): Boolean {
        return emptyQueues.compareAndSet(queues.size, queues.size)
    }

    fun add(element: T) {
        while (true) {
            val index = nextRand()
            val queue = queues[index]
            if (locks[index].tryLock()) {
                if (queue.isEmpty()) {
                    emptyQueues.decrementAndGet()
                }
                queue.add(element)
                locks[index].unlock()
                return
            }
        }
    }
    private fun distinctRandom(): Pair<Int, Int> {
        while (true) {
            val index1 = nextRand()
            val index2 = nextRand()
            if (index1 != index2) {
                return Pair(index1, index2)
            }
        }
    }

    fun poll(): T? {
        for (i in 0 until TRIES) {
            val indices = distinctRandom()
            val queue1 = queues[indices.first]
            val queue2 = queues[indices.second]

            if (locks[indices.first].tryLock()) {
                if (locks[indices.second].tryLock()) {
                    if (queue1.isNotEmpty() && queue2.isNotEmpty()) {
                        val el1 = queue1.peek()
                        val el2 = queue2.peek()
                        return if (comparator.compare(el1, el2) < 0) {
                            getAndUnlock(indices, queue1)
                        } else {
                            getAndUnlock((indices.second to indices.first), queue2)
                        }
                    } else if (queue1.isNotEmpty()) {
                        return getAndUnlock(indices, queue1)
                    } else if (queue2.isNotEmpty()) {
                        return getAndUnlock((indices.second to indices.first), queue2)
                    } else {
                        locks[indices.first].unlock()
                        locks[indices.second].unlock()
                        return null
                    }
                } else {
                    if (queue1.isNotEmpty()) {
                        return getAndUnlock(indices.first, queue1)
                    } else {
                        locks[indices.first].unlock()
                    }
                }
            } else {
                if (locks[indices.second].tryLock()) {
                    if (queue2.isNotEmpty()) {
                        return getAndUnlock(indices.second, queue2)
                    } else {
                        locks[indices.second].unlock()
                    }
                }
            }
        }

        return null
    }

    private fun getAndUnlock(index: Int, queue2: PriorityQueue<T>): T {
        val res = queue2.poll()
        if (queue2.isEmpty()) {
            emptyQueues.incrementAndGet()
        }
        locks[index].unlock()
        return res
    }

    private fun getAndUnlock(indices: Pair<Int, Int>, queue1: PriorityQueue<T>): T {
        locks[indices.second].unlock()
        val res = queue1.poll()
        if (queue1.isEmpty()) {
            emptyQueues.incrementAndGet()
        }
        locks[indices.first].unlock()
        return res
    }

    companion object {
        private const val TRIES = 7
    }
}



