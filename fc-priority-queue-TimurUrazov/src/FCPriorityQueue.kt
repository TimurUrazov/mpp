import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = CustomLock()
    private val cells = atomicArrayOfNulls<Operation<E>>(NUM_OF_CELLS)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val op = Operation<E>(operationType = OperationType.POLL)
        lockOrOrder(op)
        return op.element
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = Operation<E>(operationType = OperationType.PEEK)
        lockOrOrder(op)
        return op.element
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Operation(operationType = OperationType.ADD, arg = element)
        lockOrOrder(op)
    }

    private fun lockOrOrder(operation: Operation<E>) {
        if (!lock.withLock {
                operation.complete()
                help()
            }) {
            var nextCell: Int
            do {
                nextCell = nextRand()
            } while (!cells[nextCell].compareAndSet(null, operation))

            while (true) {
                if (operation.isServed()) {
                    cells[nextCell].compareAndSet(operation, null)
                    return
                }

                lock.withLock {
                    help()
                }
            }
        }
    }

    private fun nextRand() = ThreadLocalRandom.current().nextInt(NUM_OF_CELLS)

    private fun help() {
        for (i in 0 until NUM_OF_CELLS) {
            val op = cells[i].value
            if (op != null && !op.isServed()) {
                op.complete()
                cells[i].compareAndSet(op, null)
            }
        }
    }

    inner class Operation<T>(
        var status: ResultStatus = ResultStatus.UNCOMPLETED,
        val arg: T? = null,
        val operationType: OperationType,
    ) {
        var element: T? = null

        @Suppress("UNCHECKED_CAST")
        fun complete() {
            element = when (operationType) {
                OperationType.POLL -> q.poll() as T
                OperationType.PEEK -> q.peek() as T
                OperationType.ADD -> q.add(arg as E) as T
            }
            status = ResultStatus.COMPLETED
        }

        fun isServed() = status == ResultStatus.COMPLETED
    }

    companion object {
        private const val NUM_OF_CELLS = 20
    }
}

enum class ResultStatus {
    COMPLETED,
    UNCOMPLETED
}

enum class OperationType {
    POLL,
    PEEK,
    ADD
}

class CustomLock {
    private val flag = atomic(false)

    private fun tryLock() = flag.compareAndSet(false, true)

    private fun unlock() {
        flag.value = false
    }

    fun withLock(block: () -> Unit): Boolean {
        if (tryLock()) {
            try {
                block()
                return true
            } finally {
                unlock()
            }
        }
        return false
    }
}
