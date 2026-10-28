package com.tx24.spicyplayer.ui.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class InstanceLockTest {
    private val dir: File = Files.createTempDirectory("instance-lock").toFile()

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test fun aSecondCopyFindsTheLockTaken() {
        val first = InstanceLock.acquire(File(dir, "nested"))
        assertTrue(first is InstanceLock.Held)
        assertEquals(InstanceLock.TakenByAnother, InstanceLock.acquire(File(dir, "nested")))
        (first as InstanceLock.Held).release()
        val again = InstanceLock.acquire(File(dir, "nested"))
        assertTrue(again is InstanceLock.Held)
        (again as InstanceLock.Held).release()
    }

    @Test fun aFolderThatCantHoldTheLockLetsTheAppRun() {
        // A file where the folder should be.
        val notADir = File(dir, "file").apply { writeText("") }
        assertEquals(InstanceLock.Unavailable, InstanceLock.acquire(notADir))
    }
}
