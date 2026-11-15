package com.henriquesebastiao.downtify.core.data.offline

import java.io.OutputStream

/** Preserve bulk writes: FilterOutputStream's default implementation writes each byte separately. */
internal class NotifyingOutputStream(
    private val output: OutputStream,
    private val onClosed: () -> Unit,
) : OutputStream() {
    override fun write(value: Int) = output.write(value)

    override fun write(bytes: ByteArray) = output.write(bytes)

    override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)

    override fun flush() = output.flush()

    override fun close() {
        output.close()
        onClosed()
    }
}
