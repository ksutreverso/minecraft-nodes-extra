/**
 * Utils for file io.
 */
package phonon.nodes.utils

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Future

/**
 * Synchronously save string to file from given path.
 * Uses a temporary file and atomic move to prevent corruption.
 */
public fun saveStringToFile(str: String, path: Path) {
    val tempPath = path.resolveSibling("${path.fileName}.tmp")
    try {
        val buffer = ByteBuffer.wrap(str.toByteArray())
        val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(tempPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        val operation: Future<Int> = fileChannel.write(buffer, 0)
        operation.get()
        fileChannel.close()

        // atomic move temp file to original path
        Files.move(tempPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Exception) {
        e.printStackTrace()
        // clean up temp file if failed
        Files.deleteIfExists(tempPath)
    }
}

/**
 * Load long number from file
 */
public fun loadLongFromFile(path: Path): Long? {
    if (Files.exists(path)) {
        try {
            val numString = String(Files.readAllBytes(path))
            try {
                val num = numString.toLong()
                return num
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    return null
}

/**
 * Runnable task for writing string to a file, with optional callback
 * to run after writing is complete.
 */
public class FileWriteTask(
    str: String,
    val path: Path,
    val callback: (() -> Unit)? = null,
) : Runnable {
    val buffer = ByteBuffer.wrap(str.toByteArray())

    public override fun run() {
        val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        val operation: Future<Int> = fileChannel.write(buffer, 0)

        operation.get()

        if (callback != null) {
            callback.invoke()
        }
    }
}
