package com.jarves.mh.ui

/** Keep the final UTF-8 bytes of a process. Decode only complete characters for live output. */
internal class TerminalOutputTail(private val capacity: Int) {
    private val ring = ByteArray(capacity)
    private var end = 0
    private var size = 0

    init { require(capacity >= 4) }

    fun append(bytes: ByteArray, count: Int) {
        require(count in 0..bytes.size)
        val length = minOf(count, capacity)
        val start = count - length
        val first = minOf(length, capacity - end)
        bytes.copyInto(ring, end, start, start + first)
        bytes.copyInto(ring, 0, start + first, count)
        end = (end + length) % capacity
        size = minOf(capacity, size + length)
    }

    fun text(final: Boolean = false): String {
        val ordered = ByteArray(size)
        val start = (end - size + capacity) % capacity
        val first = minOf(size, capacity - start)
        ring.copyInto(ordered, 0, start, start + first)
        ring.copyInto(ordered, first, 0, size - first)
        var from = 0
        while (from < size && ordered[from].toInt() and 0xc0 == 0x80) from++
        var limit = size
        if (!final && from < size) {
            var lead = size - 1
            while (lead > from && ordered[lead].toInt() and 0xc0 == 0x80) lead--
            val value = ordered[lead].toInt() and 0xff
            val expected = when (value) {
                in 0xc2..0xdf -> 2
                in 0xe0..0xef -> 3
                in 0xf0..0xf4 -> 4
                else -> 1
            }
            if (size - lead < expected) limit = lead
        }
        return ordered.decodeToString(from, limit)
    }
}
