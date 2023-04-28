package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (stackPush(x)) {
            return
        }
        while (true) {
            val rand = nextRand()
            if (eliminationArray[rand].compareAndSet(null, x)) {
                for (i in 0 until SPIN) {
                    // spin
                }
                if (!eliminationArray[rand].compareAndSet(x, null)) {
                    return
                }
            }
            if (stackPush(x)) {
                return
            }
        }
    }

    private fun nextRand(): Int {
        return ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
    }

    private fun stackPush(x: E): Boolean {
        val curTop = top.value
        val newTop = Node(x, curTop)
        return top.compareAndSet(curTop, newTop)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val value = stackPop()
        if (value.second) {
            return value.first
        }
        while (true) {
            val rand = nextRand()
            val element = eliminationArray[rand].getAndSet(null) as? E
            if (element != null) {
                return element
            }
            val pop = stackPop()
            if (pop.second) {
                return pop.first
            }
        }
    }

    private fun stackPop(): Pair<E?, Boolean> {
        top.value?.let {
            val newTop = it.next
            if (top.compareAndSet(it, newTop)) {
                return it.x to true
            }
            return null to false
        } ?: return null to true
    }

    companion object {
        private val SPIN = 5_000
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT