package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        while (true) {
            val curNode = Node(x)
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, curNode)) {
                tail.compareAndSet(curTail, curNode)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val nextHead = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, nextHead)) {
                return nextHead.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}