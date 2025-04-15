package org.github.ewt45.winemulator

import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.IOUtils
import org.github.ewt45.winemulator.Consts.prootBin
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.security.MessageDigest


object Utils {
    private const val TAG = "Utils"
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


    /**
     * 解压一个.tar.xz压缩文件
     * @param archiveInputStream 对应压缩文件的输入流
     */
    @Throws(IOException::class)
    fun decompressTarXz(archiveInputStream: InputStream, dstDir: File) {
        if (!dstDir.exists()) dstDir.mkdirs()

        //文件->xz->tar
        XZCompressorInputStream(archiveInputStream).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tis ->
                var entry: TarArchiveEntry
                while (tis.nextEntry.also { entry = it } != null) {
                    val name = entry.name
                    val outFile = File(dstDir, name)
                    //确保父目录存在
                    outFile.parentFile?.mkdirs()
                    //如果是目录，创建目录
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    }
                    //如果是符号链接
                    else if (entry.isSymbolicLink) {
                        Os.symlink(entry.linkName, outFile.absolutePath)
//                            Log.d(TAG,"extract: 解压时发现符号链接：链接文件：${entry.name}，指向文件：${entry.linkName}")
                    }
                    //文件，解压
                    else {
                        FileOutputStream(outFile).use { os -> tis.copyTo(os) }
                        Os.chmod(outFile.absolutePath, entry.mode) //不知为何执行权限没同步过来？
                        // FileUtils.copyInputStreamToFile(tis, file); //不能用这个，会自动关闭输入流
                    }



                }
            }
        }
    }

    /**
     * 输入流内容复制到输出流。使用kt的copyTo 会自动使用buffer. autoCLose是否复制完关闭流，默认开启
     */
    fun streamCopy(input: InputStream, output: OutputStream, autoClose: Boolean = true) {
        input.copyTo(output)
        if (autoClose) {
            output.close()
            input.close()
        }
    }

    suspend fun readLinesProcessOutput(process: Process): String = withContext(Dispatchers.IO) {
        val output:String
        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
            output = lines.joinToString(separator = "\n")
        }
        return@withContext output
    }
}