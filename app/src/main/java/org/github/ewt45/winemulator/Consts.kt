package org.github.ewt45.winemulator

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlin.reflect.full.declaredMemberProperties

object Consts {
    private val TAG = "Consts"
    lateinit var cacheDir: File

    /** 用于proot绑定 /tmp 的安卓路径 */
    lateinit var tmpDir: File

    /** 此文件夹内包含各种rootfs. files/rootfs */
    lateinit var rootfsAllDir: File

    /** 当前激活的rootfs, 应该为一个指向实际rootfs的软链接. files/rootfs/current */
    lateinit var rootfsCurrDir: File

    /** 一个用于测试的alpine rootfs. files/rootfs/alpine-aarch64 */
    lateinit var alpineRootfsDir: File

    /** proot二进制文件. files/proot  */
    lateinit var prootBin: File

    /** 定义在assets中的默认值，此map中的值会优先于代码中的默认值生效。key为datastore的某个key, value为对应value */
    private lateinit var prefInAssets: Map<String, Any>

    object Ui {
        /** 最小化时的宽高dp值 */
        val minimizedIconSize = 48
    }

    /**
     * 用户偏好相关.
     * 如果assets中指定了默认值，会覆盖这里的默认值
     */
    object Pref {
        data class Item<T>(val key: Preferences.Key<T>, val default: T)
        /** 全部设置项。用于批量操作 例如导出导入，重置 */
        val allItems by lazy { getAllPrefItems() }

        val proot_bool_options by item("proot_bool_options", setOf("--root-id", "-L", "--link2symlink", "--kill-on-exit"))
        val proot_startup_cmd by item("proot_startup_cmd", "")


        /**
         * 初始化Item需要在读取assets之后，lazy的话 第一次用到Pref时Consts应该已经初始化好了吧。用lateinit的话还需要多写一行
         */
        private inline fun <reified T> item(name: String, default: T): Lazy<Item<T>> = lazy {
            val key: Preferences.Key<T> = when (T::class) {
                Set::class -> stringSetPreferencesKey(name)
                String::class -> stringPreferencesKey(name)
                Boolean::class -> booleanPreferencesKey(name)
                Int::class -> intPreferencesKey(name)
                Float::class -> floatPreferencesKey(name)
                Long::class -> longPreferencesKey(name)
                Double::class -> doublePreferencesKey(name)
                else -> throw IllegalArgumentException("Unsupported type: ${T::class.simpleName}")
            } as Preferences.Key<T>
            val finalDefault = (prefInAssets[name].takeIf { it is T } ?: default) as T
            return@lazy Item(key, finalDefault)
        }

        /** 反射获取全部设置项 */
        private fun getAllPrefItems():List<Item<Any>> {
            return Pref::class.declaredMemberProperties
                .filter { it.returnType.classifier == Pref.Item::class }
                .mapNotNull { property -> property.call(Pref) as? Pref.Item<Any> }
        }
    }

    /**
     * 初始化。使用前先调用一次
     */
    fun init(ctx: Context) {
        cacheDir = ctx.cacheDir
        cacheDir.mkdirs()

        tmpDir = File(cacheDir, "tmp")
        tmpDir.mkdirs()
//        Os.chmod(tmpDir.absolutePath, 0777)

        val fileDir = ctx.filesDir
        rootfsAllDir = File(fileDir, "rootfs")
        rootfsAllDir.mkdirs()

        rootfsCurrDir = File(rootfsAllDir, "current")

        alpineRootfsDir = File(rootfsAllDir, "alpine-aarch64") //这个等解压的时候再创建吧

        //proot从assets解压
        prootBin = File(fileDir, "proot")
        if (!prootBin.exists()) {
            Utils.streamCopy(ctx.assets.open("proot"), FileOutputStream(prootBin))
        }
        prootBin.setExecutable(true)

        //优先生效的用户偏好
        val prefInAssetsJson = IOUtils.toString(ctx.assets.open("preferences.json"), StandardCharsets.UTF_8)
        prefInAssets = Utils.Pref.deserializeFromJsonToMap(prefInAssetsJson)
    }
}




