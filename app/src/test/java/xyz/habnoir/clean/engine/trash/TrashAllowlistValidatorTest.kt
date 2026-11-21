package xyz.habnoir.clean.engine.trash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashAllowlistValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `allows known OEM trash directories and denies active media folders`() {
        val root = tempFolder.newFolder("emulated_0")
        val rootCanonical = root.canonicalPath

        val allowedDirs = listOf(
            File(root, ".Trash").apply { mkdirs() },
            File(root, ".vivo_recycle").apply { mkdirs() },
            File(root, "MIUI/Gallery/cloud/.trashBin").apply { mkdirs() },
            File(root, "DCIM/.Trash").apply { mkdirs() },
            File(root, ".ColorOS/.recycle").apply { mkdirs() },
            File(root, ".HiOS_Trash").apply { mkdirs() },
            File(root, ".XOS_Trash").apply { mkdirs() }
        )
        for (dir in allowedDirs) {
            assertTrue(
                "Expected allowed trash dir: ${dir.path}",
                TrashAllowlistValidator.isAllowedTrashDirectory(rootCanonical, dir)
            )
        }

        val forbiddenDirs = listOf(
            File(root, "DCIM/Camera").apply { mkdirs() },
            File(root, "WhatsApp/Media").apply { mkdirs() },
            File(root, "Download").apply { mkdirs() },
            File(root, "Documents").apply { mkdirs() },
            File(root, "Android/data/.trash").apply { mkdirs() }
        )
        for (dir in forbiddenDirs) {
            assertFalse(
                "Expected active folder to be forbidden: ${dir.path}",
                TrashAllowlistValidator.isAllowedTrashDirectory(rootCanonical, dir)
            )
        }
    }

    @Test
    fun `allows aged orphan trashed files only after grace period`() {
        val root = tempFolder.newFolder("emulated_orphan")
        val rootCanonical = root.canonicalPath
        val dcim = File(root, "DCIM").apply { mkdirs() }

        val agedOrphan = File(dcim, ".trashed-1739999999-IMG_01.jpg").apply { createNewFile() }
        val freshOrphan = File(dcim, ".trashed-1739999999-IMG_02.jpg").apply { createNewFile() }
        val activePhoto = File(dcim, "IMG_03.jpg").apply { createNewFile() }

        val now = System.currentTimeMillis()
        agedOrphan.setLastModified(now - 120_000L)
        freshOrphan.setLastModified(now - 5_000L)

        assertTrue(TrashAllowlistValidator.isAllowedOrphanTrashedFile(rootCanonical, agedOrphan, now))
        assertFalse(TrashAllowlistValidator.isAllowedOrphanTrashedFile(rootCanonical, freshOrphan, now))
        assertFalse(TrashAllowlistValidator.isAllowedOrphanTrashedFile(rootCanonical, activePhoto, now))
    }
}
