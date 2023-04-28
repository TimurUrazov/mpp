/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author Urazov Timur
 */

class Solution : MonotonicClock {
    private val l1 = RegularInt(0)
    private val m1 = RegularInt(0)
    private val r = RegularInt(0)
    private val m2 = RegularInt(0)
    private val l2 = RegularInt(0)

    override fun write(time: Time) {
        l1.value = time.d1
        m1.value = time.d2
        r.value = time.d3
        m2.value = time.d2
        l2.value = time.d1
    }

    override fun read(): Time {
        val v1 = l2.value
        val v2 = m2.value
        val w = r.value
        val v3 = m1.value
        val v4 = l1.value
        return if (v1 == v4) {
            if (v2 == v3) {
                Time(v4, v3, w)
            } else {
                Time(v4, v3, 0)
            }
        } else {
            Time(v4, 0, 0)
        }
    }
}