package com.tx24.spicyplayer.ui.settings

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * A lock on `app.lock` in the settings folder, held for as long as the app runs, so a second copy
 * can tell one is already writing the settings there. The operating system lets go of it when the
 * process ends, even after a crash.
 */
sealed interface InstanceLock {
    /** This copy holds the lock; keep it until the app exits. */
    class Held internal constructor(private val channel: FileChannel, private val lock: FileLock) : InstanceLock {
        fun release() {
            runCatching { lock.release() }
            runCatching { channel.close() }
        }
    }

    /** Another copy of the app holds it. */
    data object TakenByAnother : InstanceLock

    /** The folder can't be locked at all (read-only, or a file system without locks); run unguarded rather than not run. */
    data object Unavailable : InstanceLock

    companion object {
        const val FILE_NAME = "app.lock"

        fun acquire(dir: File): InstanceLock {
            val channel = try {
                dir.mkdirs()
                FileChannel.open(File(dir, FILE_NAME).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (e: IOException) {
                System.err.println("[Settings] Couldn't open the instance lock in $dir: $e")
                return Unavailable
            }
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // This process holds it already.
            } catch (e: IOException) {
                System.err.println("[Settings] Couldn't take the instance lock in $dir: $e")
                channel.close()
                return Unavailable
            }
            if (lock == null) {
                channel.close()
                return TakenByAnother
            }
            return Held(channel, lock)
        }
    }
}
