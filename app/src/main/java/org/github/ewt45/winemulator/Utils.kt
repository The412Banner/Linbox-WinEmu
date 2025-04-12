package org.github.ewt45.winemulator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object Utils {
    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 8) // 8KB缓冲区
        FileInputStream(file).use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val hashBytes = digest.digest()
        // 转换为十六进制字符串
        return@withContext hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun extractTarXz(tarXzFile: File, outputDir: File) {
        if (!outputDir.exists()) outputDir.mkdirs()

        XZCompressorInputStream(BufferedInputStream(FileInputStream(tarXzFile))).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tarIn ->
                var entry: TarArchiveEntry? = tarIn.nextEntry
                while (entry != null) {
                    val outFile = File(outputDir, entry.name)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        // 确保父目录存在
                        outFile.parentFile?.mkdirs()

                        FileOutputStream(outFile).use { out ->
                            tarIn.copyTo(out)
                        }
                    }

                    entry = tarIn.nextTarEntry
                }
            }
        }
    }
}