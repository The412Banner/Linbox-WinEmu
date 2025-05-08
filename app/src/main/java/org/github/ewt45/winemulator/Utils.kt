package org.github.ewt45.winemulator

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.system.Os
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ClipboardManager
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.IOUtils
import org.github.ewt45.winemulator.Consts.alpineRootfsDir
import org.github.ewt45.winemulator.Consts.prootBin
import org.github.ewt45.winemulator.Consts.rootfsAllDir
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference


object Utils {
    private const val TAG = "Utils"

    /**
     * 计算sha256的值。比较时注意全部转为大/小写
     */
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
        val output: String
        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
            output = lines.joinToString(separator = "\n")
        }
        return@withContext output
    }

    /**
     * 下载链接。
     * @param link http网址
     * @param dstFile 下载为该本地文件
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

    object Files {
        suspend fun writeToUri(ctx: Context, uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val result = ctx.contentResolver.openOutputStream(uri)?.use { output ->
                    IOUtils.write(content, output, StandardCharsets.UTF_8)
                }
                if (result == null)
                    throw RuntimeException("无法获取文件输出流")
            }
        }

        suspend fun readFromUri(ctx: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val jsonStr = ctx.contentResolver.openInputStream(uri)?.use { input ->
                    IOUtils.readLines(input, StandardCharsets.UTF_8).joinToString(separator = "")
                }
                if (jsonStr == null)
                    throw RuntimeException("无法获取文件输入流")
                return@runCatching jsonStr
            }
        }


    }

    object Rootfs {
        /**
         * 检查rootfs是否存在。若不存在，则下载并解压
         */
        suspend fun ensureAlpineRootfs(ctx: Activity): Unit = withContext(Dispatchers.IO) {
            if (!alpineRootfsDir.exists() || alpineRootfsDir.list()?.isEmpty() != false) {
                // 解压到rootfs文件夹，因为压缩包有一层alpine-aarch64文件夹。
                Utils.Archive.decompressTarXz(ctx.assets.open("alpine-aarch64-pd-v4.21.0.tar.xz"), rootfsAllDir)
            }
        }

        /**
         * 将某一个rootfs激活为当前rootfs（之后可通过rootfsCurrDir 获取
         */
        fun makeCurrent(rootfsDir: File) {
            Consts.rootfsCurrDir.delete()
            Os.symlink(rootfsDir.absolutePath, Consts.rootfsCurrDir.absolutePath)
        }
    }

    object Archive {
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
    }

    object Ui {

        /** 将一个悬浮窗靠向最近的一条边。嵌进去一半. */
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
         * 用于viewmodel中将 从datastore获取到的flow 转为stateflow
         */
        fun <T> ViewModel.stateInSimple(initValue: T, flow: Flow<T>): StateFlow<T> {
            return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initValue)
        }

        /**
         * 用于viewmodel中修改datastore的数据
         */
        fun <T> ViewModel.editDateStore(key: Preferences.Key<T>, value: T) {
            viewModelScope.launch { dataStore.edit { it[key] = value } }
        }
    }

    object Pref {
        private val TAG = "Utils.Pref"

        /**
         * 接收一个存储用户偏好的map,将其序列化为json
         */
        fun serializeFromMapToJson(map: Map<String, Any>): String {
            return kotlin.runCatching {
                val mapSerializer = MapSerializer(String.serializer(), PrefValueSerializer)
                return@runCatching Json.encodeToString(mapSerializer, map)
            }.onFailure { Log.e(TAG, "map转json失败", it) }.getOrNull() ?: ""

        }

        /**
         * 接收一个json字符串，将其转为map返回。map的key是datastore中对应的Key, value是对应的值
         */
        fun deserializeFromJsonToMap(json: String): Map<String, Any> {
            val _json = json.trim()
            if (_json.isEmpty()) return mapOf()
            return kotlin.runCatching {
                val mapSerializer = MapSerializer(String.serializer(), PrefValueSerializer)
                return@runCatching Json.decodeFromString<Map<String, Any>>(mapSerializer, _json)
            }.onFailure { Log.e(TAG, "获取assets/preferences.json失败\njson:$_json", it) }.getOrNull() ?: mapOf()
        }

        /**
         * 用于序列化/反序列化 偏好数据 -> json。虽说是Any 但是只处理datastore可以存的那几个类型
         */
        private object PrefValueSerializer : KSerializer<Any> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

            private val setSerializer = SetSerializer(String.serializer())

            override fun serialize(encoder: Encoder, value: Any) {
                when (value) {
                    is Boolean -> encoder.encodeBoolean(value)
                    is String -> encoder.encodeString(value)
                    is Int -> encoder.encodeInt(value)
                    is Float -> encoder.encodeFloat(value)
                    is Long -> encoder.encodeLong(value)
                    is Double -> encoder.encodeDouble(value)
                    is Set<*> -> {
                        if (value.first()?.takeIf { it is String } != null)
                            encoder.encodeSerializableValue(setSerializer, value as Set<String>)
                    }

                    else -> throw IllegalArgumentException("序列化时，Any无法转为常见类型: ${value::class}")
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
                    //这个数组每个元素是JsonLiteral(JsonPrimitive) 不是直接String
                    is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.takeIf { it.isString }?.content }.toSet()
                    else -> throw IllegalArgumentException("反序列化时，Any无法转为常见类型: $el")
                }
            }
        }
    }
}


class RateLimiter(val delayMs:Long = 1000L) {
    private val lastBlock = AtomicReference<(suspend () -> Unit)?>(null)
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * 延迟一段时间后执行一段代码。
     * 如果这段时间只内有新代码块，则之前的代码块不会被执行，且重新开始倒计时。
     * 请在同一线程内调用
     */
    fun runDelay(block: suspend () -> Unit) {
        lastBlock.set(block)
        scope.launch {
            delay(delayMs)
            if (lastBlock.get() == block) //最后一次设置之后，过了一秒没改过
                block()
        }
    }
}
