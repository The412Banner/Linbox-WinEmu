package org.github.ewt45.winemulator

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.system.Os
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okio.Buffer
import okio.source
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.CompressorInputStream
import org.apache.commons.compress.compressors.CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.apache.commons.compress.utils.InputStreamStatistics
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.github.ewt45.winemulator.Consts.Pref.Local.curr_rootfs_name
import org.github.ewt45.winemulator.Consts.rootfsAllDir
import org.github.ewt45.winemulator.Consts.rootfsCurrDir
import org.github.ewt45.winemulator.Utils.Files.selfExists
import org.github.ewt45.winemulator.ui.components.TaskReporter
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readSymbolicLink
import kotlin.math.pow
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

object Utils {
    private const val TAG = "Utils"

    /**
     * Compute the SHA-256 hash of a file. When comparing, normalise both sides to the same case.
     */
    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 8) // 8 KB buffer
        FileInputStream(file).use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val hashBytes = digest.digest()
        // Convert to hex string
        return@withContext hashBytes.joinToString("") { "%02x".format(it) }
    }


    /**
     * Copy an input stream to an output stream. Uses Kotlin's copyTo, which applies buffering automatically.
     * autoClose: whether to close both streams after copying (default true).
     */
    fun streamCopy(input: InputStream, output: OutputStream, autoClose: Boolean = true) {
        input.copyTo(output)
        if (autoClose) {
            output.close()
            input.close()
        }
    }

    suspend fun readLinesProcessOutput(process: Process): String = withContext(Dispatchers.IO) {
        val output: String
        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
            output = lines.joinToString(separator = "\n")
        }
        return@withContext output
    }

    /**
     * Download a URL to a local file.
     * @param link HTTP URL
     * @param dstFile destination file
     */
    fun downloadLink(link: String, dstFile: File) {
        val url = URL(link)
        url.openStream().use { input ->
            FileOutputStream(dstFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun createShareTextIntent(text: String): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
    }

    /** Returns the PID of a process */
    fun Process.getPid(): Int {
        try {
            val property = this::class.declaredMemberProperties.filterIsInstance<KProperty1<Process, Int>>().find { it.name == "pid" }
            if (property == null) return -1
            property.isAccessible = true
            val pid = property.get(this)
            property.isAccessible = false
            return pid
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    /**
     * Returns the PID of the X11 service process. Matches by process name (set in AndroidManifest).
     */
    fun Context.getX11ServicePid(): Int {
        return getSystemService(ActivityManager::class.java).runningAppProcesses
            .find { it.processName == "$packageName:xserver" }?.pid ?: -1
    }

    /**
     * chmod wrapper with try/catch. Pass mode as an octal string, e.g. "755".
     */
    fun chmod(file: File, mode: String) {
        try {
            Os.chmod(file.absolutePath, mode.toInt(8))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun File.notExists(): Boolean = !this.exists()

    /** Returns true if this stream begins with the zstd magic bytes */
    fun InputStream.isZstd(): Boolean = checkStreamHeaderMagic(byteArrayOf(0x28.toByte(), 0xB5.toByte(), 0x2F.toByte(), 0xFD.toByte()))

    /** Returns true if this file begins with the zstd magic bytes */
    fun File.isZstd(): Boolean = checkFileHeaderMagic(byteArrayOf(0x28.toByte(), 0xB5.toByte(), 0x2F.toByte(), 0xFD.toByte()))

    /** Returns true if the stream's leading bytes match [header] */
    private fun InputStream.checkStreamHeaderMagic(header: ByteArray): Boolean {
        try {
            val len = header.size.toLong()
            val fileHeader = source().use { source -> Buffer().also { source.read(it, len) }.readByteArray(len) }
            for (i in 0 until len.toInt())
                if (header[i] != fileHeader[i])
                    return false
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Returns true if the file's leading bytes match [header] */
    private fun File.checkFileHeaderMagic(header: ByteArray): Boolean {
        try {
            val len = header.size.toLong()
            val fileHeader = source().use { source -> Buffer().also { source.read(it, len) }.readByteArray(len) }
            for (i in 0 until len.toInt())
                if (header[i] != fileHeader[i])
                    return false
            return true
        } catch (e: Exception) {
            return false
        }
    }


    fun printStackTraceToString(e: Throwable): String = e.stackTraceToString()

    /** Returns true if the stream's next header.size bytes match [header] (reads from current position). */
    private fun InputStream.checkHeaderMagic(header: ByteArray): Boolean {
        try {
            val len = header.size.toLong()
            val fileHeader = source().use { source -> Buffer().also { source.read(it, len) }.readByteArray(len) }
            for (i in 0 until len.toInt())
                if (header[i] != fileHeader[i])
                    return false
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Returns true if this stream begins with the gzip magic bytes */
    fun InputStream.isGzip(): Boolean = checkHeaderMagic(byteArrayOf(0x1F.toByte(), 0x8B.toByte()))

    /** Returns true if this stream begins with the XZ magic bytes */
    fun InputStream.isXz(): Boolean = checkHeaderMagic(org.tukaani.xz.XZ.HEADER_MAGIC)

    /** Opens an input stream for a URI — equivalent to contentResolver.openInputStream(uri) */
    fun Context.openInput(uri: Uri): InputStream? = contentResolver.openInputStream(uri)

    /** Opens an output stream for a URI — equivalent to contentResolver.openOutputStream(uri) */
    fun Context.openOutput(uri: Uri): OutputStream? = contentResolver.openOutputStream(uri)

    /** Breadth-first traversal of these Paths and their descendants */
    inline fun MutableList<Path>.walk(callback: (Path) -> Unit) {
        while (isNotEmpty()) {
            val path = removeAt(0)
            callback(path)
            if (path.isDirectory(LinkOption.NOFOLLOW_LINKS)) addAll(path.listDirectoryEntries())
        }
    }

    /** Returns the octal permission mode of a path (e.g. 755) */
    fun Path.getMode(): Int {
        var result = 0
        //TODO verify that enum ordinals are preserved by code shrinking
        getPosixFilePermissions(LinkOption.NOFOLLOW_LINKS).forEach { item ->
            result = result or (1 * 2.0.pow((8 - item.ordinal))).toInt() // using ordinal directly should be fine
        }
        return result
    }

    /** Set in [Activity.onSaveInstanceState]; read in [Activity.onCreate] to detect recreation */
    var Bundle.activityRecreate: Boolean
        get() = this.getBoolean("activityRecreate", false)
        set(value) = this.putBoolean("activityRecreate", value)

    object Files {
        suspend fun writeToUri(ctx: Context, uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val result = ctx.contentResolver.openOutputStream(uri)?.use { output ->
                    IOUtils.write(content, output, StandardCharsets.UTF_8)
                }
                if (result == null)
                    throw RuntimeException("Unable to get file output stream")
            }
        }

        suspend fun readFromUri(ctx: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val jsonStr = ctx.contentResolver.openInputStream(uri)?.use { input ->
                    IOUtils.readLines(input, StandardCharsets.UTF_8).joinToString(separator = "")
                }
                if (jsonStr == null)
                    throw RuntimeException("Unable to get file input stream")
                return@runCatching jsonStr
            }
        }

        /** Creates a symlink. Aborts if [linkFile] already exists as a non-empty, non-symlink directory to avoid data loss. */
        fun symlink(realFile: File, linkFile: File) {
            if (linkFile.exists() && !FileUtils.isSymlink(linkFile) && !linkFile.list().isNullOrEmpty()) {
                Log.e(
                    TAG,
                    "symlink: aborting! The target path is already a non-empty directory and not a symlink — deleting it could lose files.\n realFile=$realFile, linkFile=$linkFile"
                )
                return
            }
            try {
                linkFile.delete()
                linkFile.parentFile?.mkdirs()
                Os.symlink(realFile.absolutePath, linkFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /** Wraps FileUtils.writeStringToFile and ensures the string ends with a newline. Does NOT trimIndent. */
        fun writeStringToFileWithLF(file: File, str: String, charset: Charset = StandardCharsets.UTF_8) {
            val str1 = str.takeIf { it.endsWith('\n') }?.let { it + "\n" } ?: str
            FileUtils.writeStringToFile(file, str1, charset)
        }

        /** Returns true if this file begins with the gzip magic bytes */
        fun File.isGzip(): Boolean = checkHeaderMagic(byteArrayOf(0x1F.toByte(), 0x8B.toByte()))

        /** Returns true if this file begins with the XZ magic bytes */
        fun File.isXz(): Boolean = checkHeaderMagic(org.tukaani.xz.XZ.HEADER_MAGIC)

        /** Returns true if the file's leading bytes match [header] */
        private fun File.checkHeaderMagic(header: ByteArray): Boolean {
            try {
                val len = header.size.toLong()
                val fileHeader = source().use { source -> Buffer().also { source.read(it, len) }.readByteArray(len) }
                for (i in 0 until len.toInt())
                    if (header[i] != fileHeader[i])
                        return false
                return true
            } catch (e: Exception) {
                return false
            }
        }

        /** Returns whether this path exists. For symlinks, checks the link itself (not its target). Use NIO instead of file.exists() which is unreliable for broken symlinks. */
        fun File.selfExists(): Boolean = java.nio.file.Files.exists(toPath(), LinkOption.NOFOLLOW_LINKS)
    }

    object Rootfs {
        /** Name of the alias file stored inside each rootfs directory */
        private const val ALIAS_FILE_NAME = ".alias"

        /**
         * Returns the display alias for a rootfs.
         * @param rootfsDir rootfs directory
         * @return alias, or the directory name if no alias is set
         */
        fun getAlias(rootfsDir: File): String {
            val aliasFile = File(rootfsDir, ALIAS_FILE_NAME)
            return if (aliasFile.exists()) {
                aliasFile.readText().trim().takeIf { it.isNotEmpty() } ?: rootfsDir.name
            } else {
                rootfsDir.name
            }
        }

        /**
         * Sets the display alias for a rootfs.
         * @param rootfsDir rootfs directory
         * @param alias new alias; if blank, the alias file is deleted
         */
        fun setAlias(rootfsDir: File, alias: String) {
            val aliasFile = File(rootfsDir, ALIAS_FILE_NAME)
            if (alias.isBlank()) {
                aliasFile.delete()
            } else {
                aliasFile.writeText(alias)
            }
        }

        /**
         * Activates a rootfs as the current one (accessible via rootfsCurrDir afterwards).
         * Saves the rootfs name to DataStore.
         */
        suspend fun makeCurrent(rootfsDir: File) {
            Files.symlink(rootfsDir, rootfsCurrDir)
            dataStore.edit { it[curr_rootfs_name.key] = rootfsDir.name }
        }

        /**
         * Returns true if no rootfs is available (the directory is empty or contains only the 'current' symlink).
         * The caller should prompt the user to select or install a rootfs.
         */
        fun haveNoRootfs(): Boolean {
            val currName = rootfsCurrDir.name
            return !(rootfsAllDir.listFiles()?.any { it.name != currName } ?: false)

        }

        /**
         * Installs a rootfs from a compressed archive (supports .tar.xz, .tar.gz, .tar.zst).
         * After extraction, outDir is a directory inside [Consts.rootfsAllDir] containing bin, etc, etc.
         * Runs post-extraction processing — see [postExtractRootfs].
         * Throws if the URI is not an xz, gz, or zst archive.
         * @param reporter [TaskReporter.progressValue] receives each file's compressed size. This function sets [TaskReporter.totalValue] to the total compressed file size.
         */
        suspend fun installRootfsArchive(ctx: Context, uri: Uri, reporter: TaskReporter): File = withContext(IO) {
            val tmpArchiveFile = File(Consts.tmpDir, "archive-rootfs-tmp").also { it.delete() }
            val tmpOutDir = File(Consts.tmpDir, "extracted-rootfs").also {
                FileUtils.deleteDirectory(it)
                it.mkdirs()
            }
            val compSize = ctx.contentResolver.openFileDescriptor(uri, "r").use { it?.statSize } ?: (1024 * 1024 * 1024L)

            reporter.progress(0F)
            reporter.totalValue = compSize

            // Detect compression type (gz / xz / zst)
            val compType = if (ctx.openInput(uri)?.use { it.isXz() } == true) CompressedType.XZ
            else if (ctx.openInput(uri)?.use { it.isGzip() } == true) CompressedType.GZ
            else if (ctx.openInput(uri)?.use { it.isZstd() } == true) CompressedType.TZST
            else throw RuntimeException("This file is not an xz, gz, or zst archive.")

            reporter.msg(null, "(1/3) Extracting to temporary folder...")
            reporter.totalValue = compSize
            val compressedTarInput = Archive.getCompressedInput(compType, ctx.openInput(uri))
            Archive.decompressCompressedTarStream(compressedTarInput, tmpOutDir, reporter)

            reporter.msg(null, "(2/3) Moving to target folder...")
            reporter.progress(0F)
            // Locate the rootfs root directory
            val confirmRootfsSubDirs = listOf("etc", "usr")
            val searchDirs = mutableListOf(tmpOutDir)
            var foundRootfsDir: File? = null
            while (searchDirs.size > 0 && foundRootfsDir == null) {
                val nowDir = searchDirs.removeAt(0)
                foundRootfsDir = nowDir.takeIf { it.list()?.toList()?.containsAll(confirmRootfsSubDirs) == true }
                nowDir.listFiles()?.let { searchDirs.addAll(it) }
            }
            if (foundRootfsDir == null)
                throw RuntimeException("Could not find rootfs root directory (containing etc and usr) in the extracted archive")

            // Always name using rootfs-1, rootfs-2, ... format
            var num = 1
            rootfsAllDir.list()?.let { while (it.contains("rootfs-$num")) num++ }
            val targetOutDir = File(rootfsAllDir, "rootfs-$num")
            reporter.msg("Moving rootfs: $foundRootfsDir -> $targetOutDir")

            FileUtils.moveDirectory(foundRootfsDir, targetOutDir)

            tmpArchiveFile.delete()
            FileUtils.deleteDirectory(tmpOutDir)

            // Post-extraction processing
            reporter.msg(null, "Extraction complete. Running post-processing...")
            postExtractRootfs(targetOutDir)

            // Create a default alias if none exists
            val aliasFile = File(targetOutDir, ALIAS_FILE_NAME)
            if (!aliasFile.exists()) {
                setAlias(targetOutDir, "rootfs-$num")
            }

            return@withContext targetOutDir
        }

        /**
         * Automatically extract a rootfs archive bundled in the assets directory.
         * Supported formats: rootfs.tar.xz, rootfs.tar.gz, rootfs.tar.zst
         * @param reporter progress reporter
         * @return extracted rootfs directory, or null if no archive was found
         */
        suspend fun installRootfsFromAssets(ctx: Context, reporter: TaskReporter): File? = withContext(IO) {
            // Look for a rootfs archive in assets
            val rootfsFileNames = listOf(
                "rootfs.tar.xz", "rootfs.tar.gz", "rootfs.tar.zst",
                "rootfs.tzst"
            )
            var foundFileName: String? = null
            
            for (fileName in rootfsFileNames) {
                try {
                    val inputStream = ctx.assets.open(fileName)
                    inputStream.close()
                    foundFileName = fileName
                    break
                } catch (e: Exception) {
                    // File not found, try the next one
                }
            }

            if (foundFileName == null) {
                reporter.msg("No rootfs archive found in assets")
                return@withContext null
            }

            reporter.msg("Found rootfs archive in assets: $foundFileName")
            
            val tmpOutDir = File(Consts.tmpDir, "extracted-rootfs").also {
                FileUtils.deleteDirectory(it)
                it.mkdirs()
            }
            
            // Determine compression type from the file name
            val compType = when {
                foundFileName.endsWith(".xz") -> CompressedType.XZ
                foundFileName.endsWith(".gz") -> CompressedType.GZ
                foundFileName.endsWith(".zst") || foundFileName.endsWith(".tzst") -> CompressedType.TZST
                else -> throw RuntimeException("Unsupported compression format: $foundFileName")
            }

            reporter.progress(0F)
            // Get the asset file size
            val compSize = ctx.assets.open(foundFileName).use { it.available().toLong() }
            reporter.totalValue = compSize

            reporter.msg(null, "(1/3) Extracting to temporary folder...")
            val compressedTarInput = Archive.getCompressedInput(compType, ctx.assets.open(foundFileName))
            Archive.decompressCompressedTarStream(compressedTarInput, tmpOutDir, reporter)
            
            reporter.msg(null, "(2/3) Moving to target folder...")
            reporter.progress(0F)

            // Locate the rootfs root directory
            val confirmRootfsSubDirs = listOf("etc", "usr")
            val searchDirs = mutableListOf(tmpOutDir)
            var foundRootfsDir: File? = null
            while (searchDirs.size > 0 && foundRootfsDir == null) {
                val nowDir = searchDirs.removeAt(0)
                foundRootfsDir = nowDir.takeIf { it.list()?.toList()?.containsAll(confirmRootfsSubDirs) == true }
                nowDir.listFiles()?.let { searchDirs.addAll(it) }
            }
            if (foundRootfsDir == null)
                throw RuntimeException("Could not find rootfs root directory (containing etc and usr) in the extracted archive")

            // Always name using rootfs-1, rootfs-2, ... format
            var num = 1
            rootfsAllDir.list()?.let { while (it.contains("rootfs-$num")) num++ }
            val targetOutDir = File(rootfsAllDir, "rootfs-$num")
            reporter.msg("Moving rootfs: $foundRootfsDir -> $targetOutDir")

            FileUtils.moveDirectory(foundRootfsDir, targetOutDir)

            FileUtils.deleteDirectory(tmpOutDir)

            // Post-extraction processing
            reporter.msg(null, "Extraction complete. Running post-processing...")
            postExtractRootfs(targetOutDir)

            // Create a default alias if none exists
            val aliasFile = File(targetOutDir, ALIAS_FILE_NAME)
            if (!aliasFile.exists()) {
                setAlias(targetOutDir, "rootfs-$num")
            }

            return@withContext targetOutDir
        }

        /** Creates a TarArchiveEntry for [path], stripping [removeLen] leading characters from the absolute path as the entry name. Appends '/' for directories. */
        private fun getTarEntry(path: Path, removeLen: Int): TarArchiveEntry {
            var entryName = path.absolutePathString().substring(removeLen).trim('/')
            if (path.isDirectory(LinkOption.NOFOLLOW_LINKS)) entryName += "/"
            // Symlinks must be constructed manually — the library doesn't follow symlinks for files (only directories)
            return if (path.isSymbolicLink())
                TarArchiveEntry(entryName, TarConstants.LF_SYMLINK).also { it.linkName = path.readSymbolicLink().pathString }
            else
                TarArchiveEntry(path, entryName, LinkOption.NOFOLLOW_LINKS)
        }

        /**
         * Compresses the specified rootfs into an archive and exports it.
         * The archive's top level is the rootfs folder; inside are the standard Linux directories.
         */
        suspend fun exportRootfsArchive(ctx: Context, uri: Uri, rootfsDir: File, compType: CompressedType, reporter: TaskReporter) = withContext(IO) {
            // Strip the rootfs parent directory prefix from archive entry paths
            val parentPrefixLen = rootfsDir.parentFile!!.absolutePath.length
            // PRoot-bound directories are excluded from the archive
            val ignoreRootfsSubDirs = setOf("dev", "proc", "tmp", "storage", "sys")
            val rootfsContents = rootfsDir.toPath().listDirectoryEntries().filter { !ignoreRootfsSubDirs.contains(it.name) }
            var fileCount = 0

            reporter.msg("Ignoring directories: ${ignoreRootfsSubDirs.joinToString(", ")}")
            reporter.msg(null, "Reading directory contents...")
            reporter.totalValue = -1L
            rootfsContents.toMutableList().walk { fileCount++ }
            reporter.msg("Found $fileCount files in total")

            reporter.msg(null, "Compressing all files...")
            reporter.totalValue = fileCount.toLong()
            TarArchiveOutputStream(Archive.getCompressedOutput(compType, ctx.openOutput(uri))).use { tOut ->
                // Support long file names
                tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                // Add the rootfs directory entry
                tOut.putArchiveEntry(getTarEntry(rootfsDir.toPath(), parentPrefixLen))
//                tOut.closeArchiveEntry()
                var tmpCount = 0L
                var tarEntry: TarArchiveEntry? = null
                rootfsContents.toMutableList().walk { filePath ->
                    try {
                        tmpCount++
                        reporter.progressValue(tmpCount)
                        tarEntry = getTarEntry(filePath, parentPrefixLen)
                        // Bug: entries built from a Path don't detect file symlinks — must pass name + linkFlag manually

                        tarEntry!!.mode = filePath.getMode()//PosixFilePermissions.toString(attrs.permissions()).toInt(8)
                        tOut.putArchiveEntry(tarEntry)
                        if (filePath.isRegularFile(LinkOption.NOFOLLOW_LINKS)) {
                            filePath.inputStream().use { it.copyTo(tOut) }
                        }
                        tOut.closeArchiveEntry()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        reporter.msg("Error during compression. File=${filePath.pathString}. Error=${e.stackTraceToString()}")
                    }
                }
                // Add bound directories as empty directories in the archive
                for (dir in ignoreRootfsSubDirs.filter { it != "storage" }) {
                    val path = Paths.get(rootfsDir.absolutePath, dir)
                    tOut.putArchiveEntry(getTarEntry(path, parentPrefixLen).also { if (dir == "tmp") it.mode = "777".toInt(8) })
                    tOut.closeArchiveEntry()
                }
                tOut.finish()
            }

            reporter.msg(null, "Archive export complete!")
        }

        /** Returns the currently selected rootfs.
         * Guarantees: 1. not [rootfsCurrDir] 2. prefers the last-saved selection; falls back to any available rootfs.
         */
        suspend fun getSelectedRootfs(): File? {
            val allAvailable = rootfsAllDir.list() ?: arrayOf()
            val selectedRootfs = curr_rootfs_name.get().takeUnless { it.isEmpty() || !allAvailable.contains(it) }
                ?: allAvailable.find { it != rootfsCurrDir.name }
            return if (selectedRootfs.isNullOrEmpty()) null
            else File(rootfsAllDir, selectedRootfs).takeIf { it.exists() }
        }

        /**
         * One-time post-extraction processing for a freshly extracted rootfs:
         * - Patches network configuration files
         * - Fixes symlinks produced by --link2symlink
         */
        suspend fun postExtractRootfs(rootfsDir: File) = withContext(Dispatchers.IO) {
            // From proot-distro — patch network config files
            File(rootfsDir, "/etc/resolv.conf").run {
                delete()
                writeText(
                    """
                    nameserver 8.8.8.8
                    nameserver 8.8.4.4
                    """.trimIndent().plus("\n")
                )
            }
            File(rootfsDir, "/etc/hosts").run {
                delete()
                writeText(
                    """
                    # IPv4.
                    127.0.0.1   localhost.localdomain localhost
            
                    # IPv6.
                    ::1         localhost.localdomain localhost ip6-localhost ip6-loopback
                    fe00::0     ip6-localnet
                    ff00::0     ip6-mcastprefix
                    ff02::1     ip6-allnodes
                    ff02::2     ip6-allrouters
                    ff02::3     ip6-allhosts
                    """.trimIndent().plus("\n")
                )
            }
            
            // Fix symlinks produced by --link2symlink
            fixL2sSymlinks(rootfsDir)
        }

        /**
         * Fixes symlinks produced by proot --link2symlink.
         * When importing an archive from another container, symlinks may point to the old container's
         * paths; this corrects them to the current container's paths.
         */
        private fun fixL2sSymlinks(rootfsDir: File) {
            val root = rootfsDir.toPath().toAbsolutePath()
            var fixedCount = 0
            
            try {
                java.nio.file.Files.walkFileTree(root, object : java.nio.file.SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                        runCatching {
                            if (!java.nio.file.Files.isSymbolicLink(file)) return@runCatching

                            val target = java.nio.file.Files.readSymbolicLink(file)
                            if (!target.isAbsolute) return@runCatching

                            val targetStr = target.toString()
                            // Match paths containing "rootfs/"
                            val rootfsIndex = targetStr.lastIndexOf("rootfs/")
                            if (rootfsIndex == -1) return@runCatching

                            // Extract the path after "rootfs/"
                            val afterRootfs = targetStr.substring(rootfsIndex + "rootfs/".length)
                            val firstSlash = afterRootfs.indexOf('/')
                            if (firstSlash == -1) return@runCatching

                            val internalPath = afterRootfs.substring(firstSlash)
                            val correctTarget = root.resolve(internalPath.substring(1))

                            if (target == correctTarget) return@runCatching

                            // Fix the symlink
                            java.nio.file.Files.delete(file)
                            java.nio.file.Files.createSymbolicLink(file, correctTarget)
                            fixedCount++
                        }
                        return java.nio.file.FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: java.io.IOException): java.nio.file.FileVisitResult {
                        return java.nio.file.FileVisitResult.CONTINUE
                    }
                })
                
                if (fixedCount > 0) {
                    Log.d("Rootfs", "fixL2sSymlinks: fixed $fixedCount symlink(s)")
                }
            } catch (e: Exception) {
                Log.w("Rootfs", "fixL2sSymlinks: error fixing symlinks: ${e.message}")
            }
        }
    }

    /** Represents a symlink encountered during archive extraction.
     * @param symlink path of the symlink on Android
     * @param pointTo the symlink target (may be relative, rootfs-absolute, or a Termux l2s path)
     */
    private data class SymLink(val symlink: String, val pointTo: String)

    /** Info about a single l2s chain. All paths are absolute and correct (prefixed with this app's package path). */
    private data class L2sInfo(
        var interCorrectPath: String,
        val finalCorrectPath: String,
        val interName: String = interCorrectPath.split('/').last(),
        val finalName: String = finalCorrectPath.split('/').last(),
        var hardPaths: MutableSet<String> = mutableSetOf(),
    )

    object Archive {


        /** Wraps a raw input stream in the appropriate decompressor, e.g. [XZCompressorInputStream] or [GzipCompressorInputStream] */
        fun getCompressedInput(type: CompressedType, rawInput: InputStream?): CompressorInputStream = when (type) {
            CompressedType.XZ -> XZCompressorInputStream(rawInput)
            CompressedType.GZ -> GzipCompressorInputStream(rawInput)
            CompressedType.TZST -> ZstdCompressorInputStream(rawInput)
        }

        /** Wraps a raw output stream in the appropriate compressor, e.g. [XZCompressorOutputStream] or [GzipCompressorOutputStream] */
        fun getCompressedOutput(type: CompressedType, rawOutput: OutputStream?): CompressorOutputStream<out OutputStream> = when (type) {
            CompressedType.XZ -> XZCompressorOutputStream(rawOutput)
            CompressedType.GZ -> GzipCompressorOutputStream(rawOutput)
            CompressedType.TZST -> ZstdCompressorOutputStream(rawOutput)
        }

        /**
         * Decompresses a compressed tar stream and extracts its contents to [outDir].
         * @param archiveInput decompressor stream for the archive, e.g. [XZCompressorInputStream] or [GzipCompressorInputStream]
         * @param outDir destination directory; after extraction its direct children should be usr, bin, etc, etc.
         * @param reporter [TaskReporter.progressValue] receives each file's compressed byte count. Ensure [TaskReporter.totalValue] is set before calling.
         * @param entryNameMapper maps an entry name from the archive (a) to a modified name (b); the file is extracted to File([outDir], b)
         */
        fun decompressCompressedTarStream(
            archiveInput: CompressorInputStream,
            outDir: File,
            reporter: TaskReporter = TaskReporter.Dummy,
            entryNameMapper: (String) -> String = { it },
        ) {
            if (!outDir.exists()) outDir.mkdirs()

            val symLinkList = mutableListOf<SymLink>()
            val dirModeList = mutableListOf<Pair<String, Int>>()  // directory permissions from the archive
            var extractCount = 0F // number of extracted files

            reporter.msg("Extracting files...")
            archiveInput.use { zis ->
                val statistics = zis as? InputStreamStatistics
                TarArchiveInputStream(zis).use { tis ->
                    var entry: TarArchiveEntry
                    while (tis.nextEntry.also { entry = it } != null) {
                        extractCount++
                        statistics?.let { reporter.progressValue(statistics.compressedCount) } // update extraction progress
                        val name = entryNameMapper(entry.name)
                        if (name.isEmpty())
                            continue
                        val outFile = File(outDir, name)
                        outFile.parentFile?.mkdirs()
                        try {
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                                dirModeList.add(outFile.absolutePath to entry.mode)
//                                Os.chmod(outFile.absolutePath, entry.mode) // setting mode mid-extraction can revoke write permission; apply after all files are extracted
                            }
                            else if (entry.isSymbolicLink) {
                                symLinkList.add(SymLink(outFile.absolutePath, entry.linkName))
//                                Os.symlink(entry.linkName, outFile.absolutePath) // defer until all files are extracted
                            }
                            else {
                                FileOutputStream(outFile).use { os -> tis.copyTo(os) }
                                Os.chmod(outFile.absolutePath, entry.mode) // executable bit may not transfer otherwise
                                // FileUtils.copyInputStreamToFile(tis, file) — cannot use this; it closes the stream
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            reporter.msg("Error extracting file: path=${outFile.absolutePath.substring(outDir.absolutePath.length)}. Error=${e.stackTraceToString()}")
                        }
                    }
                }
            }

            reporter.msg("Creating symlinks...")
            reporter.totalValue = symLinkList.size.toLong()
            symLinkList.forEachIndexed { idx, item ->
                reporter.progressValue(idx.toLong())
                try {
                    Os.symlink(item.pointTo, item.symlink)
                } catch (e: Exception) {
                    e.printStackTrace()
                    reporter.msg("Error creating symlink. File=$item. Error=${e.stackTraceToString()}")
                }
            }
            // Full pass first to ensure both intermediate and final files are created.

            //FIXME Setting PROOT_L2S_DIR to .l2s breaks locale-gen, and the current l2s fix moves files there, so skip for now.
            fixL2sFiles(outDir, symLinkList, reporter, skipProcess = true)

            // Directory permissions must be set after all files and symlinks are in place.
            reporter.msg("Restoring directory permissions...")
            reporter.totalValue = dirModeList.size.toLong()
            dirModeList.forEachIndexed { idx, item ->
                reporter.progressValue(idx.toLong())
                try {
                    Os.chmod(item.first, item.second)
                } catch (e: Exception) {
                    e.printStackTrace()
                    reporter.msg("Error restoring folder permissions")
                }
            }
        }

        /**
         * Fixes symlink targets in l2s files.
         * @param skipProcess when true, skips processing but keeps the .l2s directory (current l2s var causes issues)
         */
        private fun fixL2sFiles(outDir: File, symLinkList: List<SymLink>, reporter: TaskReporter, skipProcess: Boolean = false) {
            if (skipProcess) {
                reporter.msg("Skipping l2s fix; keeping .l2s folder for later processing...")
                // No longer deleting .l2s — let fixL2sSymlinks in postExtractRootfs handle it
                return
            }

            reporter.msg("Starting l2s file fix...")

            /** l2s file info. Key = wrong intermediate path (foreign package prefix); value = related info. */
            val interToL2sMap: MutableMap<String, L2sInfo> = mutableMapOf()

            // Prefer files from /.l2s; fall back to the hard-link's directory
            val l2sDir = File(outDir, "/.l2s")
            val l2sDirFiles = l2sDir.listFiles() ?: arrayOf()
            val regex4Dec = "^[0-9]{4}$".toRegex()
            for (idx in symLinkList.indices) {
                val item = symLinkList[idx]
                reporter.progressValue(idx.toLong())
                // Fix symlink targets for proot l2s files. Entries may be hard-link->intermediate or intermediate->final.
                // File.exists() is unreliable for symlinks — use NIO Files.exists with NOFOLLOW_LINKS.
                try {
                    val interPrefix = ".l2s."
                    // Hard-link-simulated filename (skip if this is an intermediate file itself)
                    val hardFile = File(item.symlink).takeIf { it.selfExists() && !it.name.startsWith(interPrefix) }
                        ?: continue
                    // Wrong intermediate target path — format: .l2s. + any text + .4-digit-number
                    val interWrongFile = File(item.pointTo).takeIf { it.name.startsWith(interPrefix) && it.name.takeLast(4).matches(regex4Dec) }
                        ?: continue
                    val interName = interWrongFile.name
                    // Current location of the intermediate file: in .l2s or the hard-link's directory
                    val interExistFile = (l2sDirFiles.find { it.name == interName } ?: File(hardFile.parent!!, interName).takeIf { it.selfExists() })
                        ?: throw RuntimeException("Hard-link file found but intermediate file is missing.")
                    // Wrong final target path: intermediate file is a symlink pointing to it

                    val finalWrongFile = java.nio.file.Files.readSymbolicLink(interExistFile.toPath()).toFile()
                        ?: throw RuntimeException("Hard-link and intermediate file exist but the intermediate is not a symlink.")
                    val finalPrefix = "$interName."
                    // Final filename: intermediate name + . + hard-link count
                    val finalName = finalWrongFile.name.takeIf { it.startsWith(finalPrefix) && it.substring(finalPrefix.length).matches(regex4Dec) }
                        ?: throw RuntimeException("Hard-link and intermediate file exist but the final filename format is wrong.")
                    // Current location of the final file: in .l2s or the intermediate's directory
                    val finalExistFile =
                        (l2sDirFiles.find { it.name == finalName } ?: File(interExistFile.parent!!, finalName).takeIf { it.selfExists() })
                            ?: throw RuntimeException("Hard-link and intermediate file exist but the final file is missing.")

                    // Collect everything first; fixing paths mid-loop could make later lookups find the corrected path.
                    val l2sInfo = interToL2sMap[interWrongFile.absolutePath]
                        ?: L2sInfo(interExistFile.absolutePath, finalExistFile.absolutePath, interName, finalName)
                            .also { interToL2sMap[interWrongFile.absolutePath] = it }
                    l2sInfo.hardPaths.add(hardFile.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                    reporter.msg("Error locating l2s file. Data=$item. Error=${e.stackTraceToString()}")
                }
            }

            reporter.msg("l2s file scan complete. Starting fix...")
            reporter.totalValue = interToL2sMap.size.toLong()

            /** Fix all wrong l2s-related symlinks found above: intermediate->final, hard-link->intermediate */
            interToL2sMap.values.forEachIndexed { idx, info ->
                reporter.progressValue(idx.toLong())
                reporter.msg("Fixing l2s symlink $info")
                try {
                    // Force everything into .l2s — proot will point PROOT_L2S_DIR there at startup
                    var finalInL2s = File(info.finalCorrectPath)
                    if (!finalInL2s.absolutePath.startsWith(l2sDir.absolutePath))
                        finalInL2s = File(l2sDir, finalInL2s.name).also { FileUtils.moveFile(finalInL2s, it) }
                    var interInL2s = File(info.interCorrectPath).also { it.delete() }
                    if (!interInL2s.absolutePath.startsWith(l2sDir.absolutePath))
                        interInL2s = File(l2sDir, interInL2s.name)
                    Os.symlink(finalInL2s.absolutePath, interInL2s.absolutePath)
                    info.hardPaths.forEach {
                        File(it).delete()
                        Os.symlink(interInL2s.absolutePath, it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    reporter.msg("Error creating corrected l2s symlink. Data=$info. Error=${e.stackTraceToString()}")
                }
            }

            reporter.msg("l2s file fix complete")
            //TODO Consider a second pass over the symlink list to catch any remaining Termux-pointing entries.
            // Currently only hard-link entries are checked; intermediate-only entries are skipped (though they normally imply a hard-link exists).

        }

        /**
         * Extracts a .tar.xz archive.
         * @param archiveInput raw input stream for the archive
         * @param outDir destination directory; after extraction its direct children should be usr, bin, etc, etc.
         * @param reporter [TaskReporter.progressValue] receives each file's compressed byte count. Ensure [TaskReporter.totalValue] is set before calling.
         * @param entryNameMapper maps an entry name from the archive (a) to a modified name (b); the file is extracted to File([outDir], b)
         */
        @Throws(IOException::class)
        fun decompressTarXz(
            archiveInput: InputStream?,
            outDir: File,
            reporter: TaskReporter = TaskReporter.Dummy,
            entryNameMapper: (String) -> String = { it },
        ) {
            XZCompressorInputStream(archiveInput).use { decompressCompressedTarStream(it, outDir, reporter, entryNameMapper) }
        }
    }

    object Ui {

        /** Snaps a floating window to the nearest edge, embedding it halfway off-screen. */
        fun View.snapToNearestEdgeHalfway() {
            val parent = parent as? View ?: return
            val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return

            val snapDistanceLeft = left
            val snapDistanceRight = parent.width - right
            val snapDistanceTop = top
            val snapDistanceBottom = parent.height - bottom

            val minDistance = minOf(snapDistanceLeft, snapDistanceRight, snapDistanceTop, snapDistanceBottom)

            val currentLeft = left
            val currentTop = top
            var targetLeft = currentLeft
            var targetTop = currentTop

            when (minDistance) {
                snapDistanceLeft -> targetLeft = -width / 2
                snapDistanceRight -> targetLeft = parent.width - width / 2
                snapDistanceTop -> targetTop = -height / 2
                snapDistanceBottom -> targetTop = parent.height - height / 2
            }

            ValueAnimator.ofInt(currentLeft, targetLeft).apply {
                duration = 300
                addUpdateListener { animation ->
                    lp.leftMargin = animation.animatedValue as Int
                    requestLayout()
                }
            }.start()

            ValueAnimator.ofInt(currentTop, targetTop).apply {
                duration = 300
                addUpdateListener { animation ->
                    lp.topMargin = animation.animatedValue as Int
                    requestLayout()
                }
            }.start()
        }

        /**
         * Converts a DataStore flow into a StateFlow for use in ViewModels.
         */
        fun <T> ViewModel.stateInSimple(initValue: T, flow: Flow<T>): StateFlow<T> {
            return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initValue)
        }

        /**
         * Writes a value to DataStore from a ViewModel, on the IO dispatcher.
         */
        suspend fun <T> ViewModel.editDateStore(key: Preferences.Key<T>, value: T) = withContext(Dispatchers.IO) {
            dataStore.edit { it[key] = value }
        }

        /** Same as [ViewModel.editDateStore] but launches a new coroutine and returns immediately. */
        fun <T> ViewModel.editDateStoreAsync(key: Preferences.Key<T>, value: T) {
            viewModelScope.launch(Dispatchers.IO) { dataStore.edit { it[key] = value } }
        }
    }

    object Permissions {
        private var requestLauncher: ActivityResultLauncher<String>? = null
        private var notificationRequestLauncher: ActivityResultLauncher<String>? = null

        /** Invoked once when the permission grant result is returned */
        private var requestLauncherCallback: ((Boolean) -> Unit)? = null

        /**
         * Call from the Activity's init block to register result callbacks.
         * Also registers a lifecycle observer that unregisters the launchers on onDestroy.
         */
        fun registerForActivityResult(a: MainEmuActivity) {
            val onActivityResult: (Boolean) -> Unit = { isGranted ->
                requestLauncherCallback?.invoke(isGranted)
                requestLauncherCallback = null
            }
            requestLauncher = a.registerForActivityResult(ActivityResultContracts.RequestPermission(), onActivityResult)
            notificationRequestLauncher = a.registerForActivityResult(object : ActivityResultContract<String, Boolean>() {
                override fun createIntent(context: Context, input: String) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .apply { putExtra(Settings.EXTRA_APP_PACKAGE, MainEmuApplication.i.packageName) }

                override fun parseResult(resultCode: Int, intent: Intent?) =
                    NotificationManagerCompat.from(MainEmuApplication.i).areNotificationsEnabled()

                override fun getSynchronousResult(context: Context, input: String): SynchronousResult<Boolean>? {
                    val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    return if (granted) SynchronousResult(true) else null
                }

            }, callback = onActivityResult)

            // Unregister launchers on destroy to avoid leaks
            a.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    requestLauncher?.unregister()
                    requestLauncher = null
                    notificationRequestLauncher?.unregister()
                    notificationRequestLauncher = null
                }
            })
        }

        /** Returns true if the given permission has been granted */
        fun isGranted(ctx: Context, permission: String): Boolean {
            if (permission == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            }
            return ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Requests a permission. Do not call again before the previous result is returned,
         * or the onResult callback will be overwritten.
         * @param onResult callback after the request; parameter is isGranted
         */
        fun request(permission: String, onResult: (Boolean) -> Unit) {
            requestLauncherCallback = onResult
            if (permission == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                notificationRequestLauncher?.launch(permission)
            } else {
                requestLauncher?.launch(permission)
            }
        }
    }

    object Pref {
        private const val TAG = "Utils.Pref"

        /**
         * Serializes a user-preference map to a JSON string.
         */
        fun serializeFromMapToJson(map: Map<String, Any>): String {
            return kotlin.runCatching {
                val mapSerializer = MapSerializer(String.serializer(), PrefValueSerializer)
                return@runCatching Json.encodeToString(mapSerializer, map)
            }.onFailure { Log.e(TAG, "map->json serialization failed", it) }.getOrNull() ?: ""

        }

        /**
         * Deserializes a JSON string into a map. Keys are DataStore key names; values are the stored values.
         */
        fun deserializeFromJsonToMap(json: String): Map<String, Any> {
            val _json = json.trim()
            if (_json.isEmpty()) return mapOf()
            return kotlin.runCatching {
                val mapSerializer = MapSerializer(String.serializer(), PrefValueSerializer)
                return@runCatching Json.decodeFromString<Map<String, Any>>(mapSerializer, _json)
            }.onFailure { Log.e(TAG, "Failed to parse preferences JSON\njson:$_json", it) }.getOrNull() ?: mapOf()
        }

        /**
         * Serializer/deserializer for preference data ↔ JSON. Handles the types that DataStore can store (Boolean, String, Int, Float, Long, Double, Set<String>).
         */
        private object PrefValueSerializer : KSerializer<Any> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

            private val listSerializer = ListSerializer(String.serializer())

            override fun serialize(encoder: Encoder, value: Any) {
                when (value) {
                    is Boolean -> encoder.encodeBoolean(value)
                    is String -> encoder.encodeString(value)
                    is Int -> encoder.encodeInt(value)
                    is Float -> encoder.encodeFloat(value)
                    is Long -> encoder.encodeLong(value)
                    is Double -> encoder.encodeDouble(value)
                    is Set<*> -> {
                        encoder.encodeSerializableValue(listSerializer, value.map { it as String })
//                        if (value.first()?.takeIf { it is String } != null) encoder.encodeSerializableValue(setSerializer, value as Set<String>)
                    }

                    else -> throw IllegalArgumentException("Serialization: unsupported type for Any: ${value::class}")
                }
            }

            override fun deserialize(decoder: Decoder): Any {
                return when (val el = (decoder as JsonDecoder).decodeJsonElement()) {
                    is JsonPrimitive -> {
                        when {
                            el.isString -> el.content
                            el.booleanOrNull is Boolean -> el.content.toBoolean()
                            el.intOrNull is Int -> el.content.toInt()
                            el.floatOrNull is Float -> el.content.toFloat()
                            el.longOrNull is Long -> el.content.toLong()
                            el.doubleOrNull is Double -> el.content.toDouble()
                            else -> el.content
                        }
                    }
                    // Each array element is a JsonLiteral (JsonPrimitive), not a plain String
                    is JsonArray -> {
                        el.mapNotNull { item -> (item as JsonPrimitive).takeIf { it.isString }!!.content }.toSet()
                    }

                    else -> throw IllegalArgumentException("Deserialization: unsupported JSON element type for Any: $el")
                }
            }
        }
    }

}


class RateLimiter(val delayMs: Long = 1000L) {
    private val lastBlock = AtomicReference<(suspend () -> Unit)?>(null)
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Schedules [block] to run after [delayMs]. If a new block is submitted before the delay
     * elapses, the previous one is cancelled and the countdown restarts.
     * Must be called from the same thread.
     */
    fun runDelay(block: suspend () -> Unit) {
        lastBlock.set(block)
        scope.launch {
            delay(delayMs)
            if (lastBlock.get() == block) // still the latest submission after the delay
                block()
        }
    }
}

enum class CompressedType {
    XZ, GZ, TZST,
}

enum class FuncOnChangeAction {
    EDIT,
    ADD,
    DEL,
}
/** Synchronous callback for add/edit/delete operations. Synchronous version of [FuncOnChange]. */
typealias FuncOnChangeSync<T> = (oldValue: T, newValue: T, action: FuncOnChangeAction) -> Unit
/** Asynchronous callback for add/edit/delete operations. For ADD or DEL, old == new. */
typealias FuncOnChange<T> = suspend (oldValue: T, newValue: T, action: FuncOnChangeAction) -> Unit

