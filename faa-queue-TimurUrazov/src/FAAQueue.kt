package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    // okay, global indices were not a trap, but now I know how to do lock-free queue with local indices
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val index = enqIdx.getAndIncrement()
            var curSegment = curTail.value
            while (curSegment.id < index / SEGMENT_SIZE) {
                val next = curSegment.next.value
                if (next == null) {
                    curSegment.next.compareAndSet(null, Segment(curSegment.id + 1))
                }
                curSegment = curSegment.next.value!!
            }
            if (curSegment.cas((index % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val curHead = head.value
            val index = deqIdx.getAndIncrement()
            var currentSegment = curHead
            while (currentSegment.id < index / SEGMENT_SIZE) {
                val next = currentSegment.next.value
                if (next == null) {
                    currentSegment.next.compareAndSet(null, Segment(currentSegment.id + 1))
                }
                currentSegment = currentSegment.next.value!!
            }
            head.compareAndSet(curHead, currentSegment)
            if (currentSegment.cas((index % SEGMENT_SIZE).toInt(), null, Any())) {
                continue
            }
            return currentSegment.get((index % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
