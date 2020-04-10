/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.jcr

import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.jcr.Binary
import javax.jcr.Node


private val log = KotlinLogging.logger {}

@Service
open class RepoBackupService {
    @Autowired
    internal lateinit var repoService: RepoService

    /**
     * @param absPath If not given, [RepoService.mainNodeName] is used.
     */
    open fun backupAsZipArchive(archiveName: String, zipOut: ZipOutputStream, absPath: String = "/${repoService.mainNodeName}") {
        val archivNameWithoutExtension = if (archiveName.contains('.')) {
            archiveName.substring(0, archiveName.indexOf('.'))
        } else {
            archiveName
        }
        return runInSession { session ->
            log.info { "Creating backup of document view and binaries of path '$absPath' as '$archiveName'..." }

            // Write README.TXT
            zipOut.putNextEntry(createZipEntry(archivNameWithoutExtension, "README.TXT"))
            val readme = this::class.java.getResource("/backupReadme.txt").readText()
            zipOut.write(readme.toByteArray(StandardCharsets.UTF_8))

            val topNode = repoService.getNode(session, absPath, null)
            // Using repository.json if repository.xml doesn't work.
            zipOut.putNextEntry(createZipEntry(archivNameWithoutExtension, "repository.json"))
            zipOut.write(PFJcrUtils.toJson(NodeInfo(topNode, recursive = true)).toByteArray(StandardCharsets.UTF_8))
            writeToZip(topNode, archivNameWithoutExtension, zipOut)
        }
    }

    /**
     * @param absPath If not given, [RepoService.mainNodeName] is used (only used for creation of repository.xml).
     */
    @JvmOverloads
    open fun restoreBackupFromZipArchive(zipIn: ZipInputStream, securityConfirmation: String, absPath: String = "/${repoService.mainNodeName}") {
        if (securityConfirmation != RESTORE_SECURITY_CONFIRMATION__I_KNOW_WHAT_I_M_DOING__REPO_MAY_BE_DESTROYED) {
            throw IllegalArgumentException("You must use the correct security confirmation if you know what you're doing. The repo content may be lost after restoring!")
        }
        return runInSession { session ->
            log.info { "Restoring backup of document view and binaries of path '$absPath'..." }
            var nodesRestored = false
            var zipEntry = zipIn.nextEntry
            while (zipEntry != null) {
                if (zipEntry.isDirectory) {
                    zipEntry = zipIn.nextEntry
                    continue
                }
                val fileName = FilenameUtils.getName(zipEntry.name)
                if (!nodesRestored) {
                    if (fileName == "repository.json") {
                        log.info { "Restoring nodes from '${zipEntry.name}'..." }
                        val json = zipIn.readBytes().toString(StandardCharsets.UTF_8)
                        val topNode = PFJcrUtils.fromJson(json, NodeInfo::class.java)
                        restoreNode(session.rootNode, topNode)
                        session.save()
                        nodesRestored = true
                        zipEntry = zipIn.nextEntry
                        continue
                    }
                }
                val filesPath = getFilesPath(zipEntry.name)
                if (!filesPath.isNullOrBlank() && !IGNORE_FILES.contains(fileName)) {
                    if (log.isDebugEnabled) {
                        log.debug { "Restoring file content (binary) '${zipEntry.name}', $fileName..." }
                    }
                    val filesNode = repoService.getNodeOrNull(session, filesPath)
                    if (filesNode == null) {
                        log.error { "Can't determine node '$filesNode'. Can't restore binary '${zipEntry.name}'." }
                        zipEntry = zipIn.nextEntry
                        continue
                    }
                    val fileNode = repoService.findFile(filesNode, FilenameUtils.getBaseName(zipEntry.name))
                    if (fileNode == null) {
                        log.error { "Can't determine node '$fileNode'. Can't restore binary '${zipEntry.name}'." }
                        zipEntry = zipIn.nextEntry
                        continue
                    }
                    if (!nodesRestored) {
                        throw IllegalArgumentException("Sorry, can't restore binaries. repository.xml must be read first (placed before restoring binaries in zip file)!")
                    }
                    val fileObject = FileObject(fileNode)
                    log.info { "Restoring file '${zipEntry.name}': $fileObject" }
                    val content = zipIn.readBytes()
                    val inputStream = ByteArrayInputStream(content)
                    val bin: Binary = session.valueFactory.createBinary(inputStream)
                    fileNode.setProperty(RepoService.PROPERTY_FILECONTENT, session.valueFactory.createValue(bin))
                    session.save()
                }
                zipEntry = zipIn.nextEntry
            }
            zipIn.closeEntry()
        }
    }

    private fun restoreNode(parentNode: Node, nodeInfo: NodeInfo) {
        val node = repoService.ensureNode(parentNode, nodeInfo.name)
        nodeInfo.properties?.forEach {
            it.addToNode(node)
        }
        nodeInfo.children?.forEach {
            restoreNode(node, it)
        }
    }

    private fun getFilesPath(fileName: String): String? {
        if (!fileName.contains(RepoService.NODENAME_FILES)) {
            return null
        }
        var archiveName = fileName.substring(fileName.indexOf('/'))
        if (archiveName.startsWith("//")) {
            archiveName = archiveName.substring(1)
        }
        archiveName = archiveName.substring(0, archiveName.indexOf(RepoService.NODENAME_FILES) - 1)
        return "$archiveName/${RepoService.NODENAME_FILES}"
    }

    private fun writeToZip(node: Node, archiveName: String, zipOut: ZipOutputStream) {
        val fileList = repoService.getFiles(node)
        if (!fileList.isNullOrEmpty()) {
            fileList.forEach {
                val fileNode = repoService.findFile(node, it.id, null)
                val content = repoService.getFileContent(fileNode)
                if (content != null) {
                    val fileName = PFJcrUtils.createSafeFilename(it)
                    zipOut.putNextEntry(createZipEntry(archiveName, node.path, fileName))
                    zipOut.write(content)
                }
            }
            zipOut.putNextEntry(createZipEntry(archiveName, node.path, "files.json"))
            zipOut.write(PFJcrUtils.toJson(FileObjectList(fileList)).toByteArray(StandardCharsets.UTF_8))
            zipOut.putNextEntry(createZipEntry(archiveName, node.path, "files.txt"))
            val fileListAsString = fileList.joinToString(separator = "\n") { "${PFJcrUtils.createSafeFilename(it)} ${PFJcrUtils.formatBytes(it.size)} ${it.fileName}" }
            zipOut.write(fileListAsString.toByteArray(StandardCharsets.UTF_8))
        }
        val nodeInfo = NodeInfo(node, false)
        zipOut.putNextEntry(createZipEntry(archiveName, node.path, "node.json"))
        zipOut.write(PFJcrUtils.toJson(nodeInfo).toByteArray(StandardCharsets.UTF_8))
        node.nodes?.let {
            while (it.hasNext()) {
                writeToZip(it.nextNode(), archiveName, zipOut)
            }
        }
    }

    private fun createZipEntry(archiveName: String, vararg path: String?): ZipEntry {
        return ZipEntry("$archiveName/${path.joinToString(separator = "/") { it ?: "" }}")
    }

    private fun <T> runInSession(method: (session: SessionWrapper) -> T): T {
        val session = SessionWrapper(this.repoService)
        try {
            return method(session)
        } finally {
            session.logout()
        }
    }

    companion object {
        const val RESTORE_SECURITY_CONFIRMATION__I_KNOW_WHAT_I_M_DOING__REPO_MAY_BE_DESTROYED = "Yes, I want to restore the repo and know what I'm doing. The repo may be lost."

        private val IGNORE_FILES = arrayOf("README.txt", "node.json", "files.txt", "files.json")
    }
}