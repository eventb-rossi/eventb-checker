package com.eventb.checker

import com.eventb.checker.TestZipHelper.createZip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelImporterTest {

    @TempDir
    lateinit var tempDir: File

    private val importer = ModelImporter()

    @Test
    fun `import categorizes machine and context files`() {
        val zip = createZip(
            tempDir,
            "project/MyMachine.bum" to "<org.eventb.core.machineFile/>",
            "project/MyContext.buc" to "<org.eventb.core.contextFile/>",
            "project/.project" to "<projectDescription/>",
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.machines[0].path).isEqualTo("project/MyMachine.bum")
        assertThat(contents.contexts).hasSize(1)
        assertThat(contents.contexts[0].path).isEqualTo("project/MyContext.buc")
    }

    @Test
    fun `import handles empty zip`() {
        val zip = createZip(tempDir)

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).isEmpty()
        assertThat(contents.contexts).isEmpty()
    }

    @Test
    fun `import handles nested directories`() {
        val zip = createZip(
            tempDir,
            "root/sub/Deep.bum" to "<org.eventb.core.machineFile/>",
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.machines[0].path).isEqualTo("root/sub/Deep.bum")
    }

    @Test
    fun `import normalizes backslashes in zip entry paths`() {
        val zip = createZip(
            tempDir,
            "root\\sub\\Deep.bum" to "<org.eventb.core.machineFile/>",
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.machines[0].path).isEqualTo("root/sub/Deep.bum")
    }

    @Test
    fun `import rejects non-zip file`() {
        val notZip = File(tempDir, "test.txt")
        notZip.writeText("not a zip")

        assertThatThrownBy { importer.import(notZip.absolutePath) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Expected a .zip file, directory, or .eventb file")
    }

    @Test
    fun `import rejects non-existent file`() {
        assertThatThrownBy { importer.import("/nonexistent/path.zip") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `import categorizes machine and context files from directory`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "MyMachine.bum").writeText("<org.eventb.core.machineFile/>")
        File(projectDir, "MyContext.buc").writeText("<org.eventb.core.contextFile/>")
        File(projectDir, ".project").writeText("<projectDescription/>")

        val contents = importer.import(projectDir.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.machines[0].path).isEqualTo("project/MyMachine.bum")
        assertThat(contents.contexts).hasSize(1)
        assertThat(contents.contexts[0].path).isEqualTo("project/MyContext.buc")
    }

    @Test
    fun `import categorizes mixed bum buc eventb files correctly`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        File(projectDir, "M.bum").writeText("<org.eventb.core.machineFile/>")
        File(projectDir, "C.buc").writeText("<org.eventb.core.contextFile/>")
        File(projectDir, "M2.eventb").writeText("machine M2 end")
        File(projectDir, ".project").writeText("<projectDescription/>")

        val contents = importer.import(projectDir.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.contexts).hasSize(1)
        assertThat(contents.eventbFiles).hasSize(1)
    }

    @Test
    fun `import categorizes eventb files from zip`() {
        val zip = createZip(
            tempDir,
            "project/MyMachine.eventb" to "machine MyMachine end",
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.eventbFiles).hasSize(1)
        assertThat(contents.eventbFiles[0].path).isEqualTo("project/MyMachine.eventb")
        assertThat(contents.machines).isEmpty()
        assertThat(contents.contexts).isEmpty()
    }

    @Test
    fun `import directory produces correct file content`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        val xmlContent = "<org.eventb.core.machineFile name=\"Test\"/>"
        File(projectDir, "Test.bum").writeText(xmlContent)

        val contents = importer.import(projectDir.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(String(contents.machines[0].data)).isEqualTo(xmlContent)
    }

    @Test
    fun `import directory files in normalized sorted order`() {
        val projectDir = File(tempDir, "project")
        File(projectDir, "b").mkdirs()
        File(projectDir, "a").mkdirs()
        File(projectDir, "b/Z.bum").writeText("<org.eventb.core.machineFile/>")
        File(projectDir, "a/A.bum").writeText("<org.eventb.core.machineFile/>")

        val contents = importer.import(projectDir.absolutePath)

        // Files under subdirectories are keyed by their subdirectory (each is its own project),
        // so the "project/" input-directory name is not prepended; import order stays sorted.
        assertThat(contents.machines.map { it.path }).containsExactly(
            "a/A.bum",
            "b/Z.bum",
        )
    }

    @Test
    fun `import zip files in normalized sorted order`() {
        val zip = createZip(
            tempDir,
            "project/B.bum" to "<org.eventb.core.machineFile name=\"B\"/>",
            "project/A.bum" to "<org.eventb.core.machineFile name=\"A\"/>",
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines.map { it.path }).containsExactly(
            "project/A.bum",
            "project/B.bum",
        )
    }

    @Test
    fun `import single eventb file`() {
        val projectDir = File(tempDir, "project")
        projectDir.mkdirs()
        val eventbFile = File(projectDir, "Counter.eventb")
        eventbFile.writeText("machine Counter\nend")

        val contents = importer.import(eventbFile.absolutePath)

        assertThat(contents.eventbFiles).hasSize(1)
        assertThat(contents.eventbFiles[0].path).isEqualTo("project/Counter.eventb")
        assertThat(String(contents.eventbFiles[0].data)).isEqualTo("machine Counter\nend")
        assertThat(contents.machines).isEmpty()
        assertThat(contents.contexts).isEmpty()
    }

    @Test
    fun `import categorizes proof files from zip`() {
        val zip = createZip(
            tempDir,
            "project/M0.bum" to "<org.eventb.core.machineFile/>",
            "project/M0.bpr" to "<org.eventb.core.prFile/>",
            "project/M0.bpo" to "<org.eventb.core.poFile/>",
            "project/M0.bps" to "<org.eventb.core.psFile/>",
        )

        val contents = importer.import(zip.absolutePath)

        assertThat(contents.machines).hasSize(1)
        assertThat(contents.proofFiles).hasSize(1)
        assertThat(contents.proofFiles[0].path).isEqualTo("project/M0.bpr")
        assertThat(contents.proofObligationFiles).hasSize(1)
        assertThat(contents.proofObligationFiles[0].path).isEqualTo("project/M0.bpo")
        assertThat(contents.proofStatusFiles).hasSize(1)
        assertThat(contents.proofStatusFiles[0].path).isEqualTo("project/M0.bps")
    }
}
