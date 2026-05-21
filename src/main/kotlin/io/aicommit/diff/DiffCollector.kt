package io.aicommit.diff

import com.intellij.openapi.components.Service
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import git4idea.GitUtil
import java.io.File
import java.io.StringWriter
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class DiffCollector(private val project: Project) {

    fun collect(changes: List<Change>): DiffPayload {
        val diff = renderUnifiedDiff(changes)
        val files = changes.mapNotNull { it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path }
        val branch = currentBranch()
        val recent = recentCommitSubjects(limit = 10)
        return DiffPayload(diff = diff, files = files, recentCommits = recent, branch = branch)
    }

    private fun renderUnifiedDiff(changes: List<Change>): String {
        if (changes.isEmpty()) return ""
        val basePath: Path = project.basePath?.let { Path.of(it) } ?: return ""
        val patches = IdeaTextPatchBuilder.buildPatch(project, changes, basePath, false, false)
        val sw = StringWriter()
        UnifiedDiffWriter.write(project, basePath, patches, sw, "\n", null, null)
        return sw.toString()
    }

    private fun currentBranch(): String {
        val repo = GitUtil.getRepositoryManager(project).repositories.firstOrNull() ?: return ""
        return repo.currentBranchName.orEmpty()
    }

    private fun recentCommitSubjects(limit: Int): List<String> {
        val repo = GitUtil.getRepositoryManager(project).repositories.firstOrNull() ?: return emptyList()
        val root = File(repo.root.path)
        return try {
            val proc = ProcessBuilder("git", "log", "--format=%s", "-n", limit.toString())
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly(); return emptyList()
            }
            if (proc.exitValue() != 0) emptyList()
            else output.lineSequence().filter { it.isNotBlank() }.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
