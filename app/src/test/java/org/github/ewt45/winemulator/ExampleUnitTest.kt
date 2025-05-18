package org.github.ewt45.winemulator

import org.junit.Test

import org.junit.Assert.*
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }


    private data class SymLink(val symlink: String, val pointTo: String)

    @Test
    fun fun1() {
        """
SymLink(symlink=/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/.l2s/.l2s.passwd0001, pointTo=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.passwd0001.0001)
SymLink(symlink=/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/.l2s/.l2s.shadow0001, pointTo=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.shadow0001.0001)
SymLink(symlink=/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/.l2s/.l2s.group0001, pointTo=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.group0001.0001)
SymLink(symlink=/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/etc/group-, pointTo=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.group0001)
SymLink(symlink=/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/etc/passwd-, pointTo=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.passwd0001)
SymLink(symlink=/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/etc/shadow-, pointTo=/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.shadow0001)

       """.trimIndent()

        val symlinkList = listOf(
            SymLink(
                symlink = "/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/.l2s/.l2s.passwd0001",
                pointTo = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.passwd0001.0001"
            ),
            SymLink(
                symlink = "/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/.l2s/.l2s.shadow0001",
                pointTo = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.shadow0001.0001"
            ),
            SymLink(
                symlink = "/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/.l2s/.l2s.group0001",
                pointTo = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.group0001.0001"
            ),
            SymLink(
                symlink = "/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/etc/group-",
                pointTo = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.group0001"
            ),
            SymLink(
                symlink = "/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/etc/passwd-",
                pointTo = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.passwd0001"
            ),
            SymLink(
                symlink = "/data/user/0/a.io.github.ewt45.winemulator/files/rootfs/rootfs-1/etc/shadow-",
                pointTo = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/alpine/.l2s/.l2s.shadow0001"
            ),
            )

        for( item in symlinkList) {
            val regex4Dec = "^[0-9]{4}$".toRegex()
            val hardFile = File(item.symlink).takeIf { it.exists() } ?: continue
            val interPrefix = ".l2s."
            // 中间文件 错误指向的 那个不存在路径
            val interWrongFile = File(item.pointTo)
            //中间文件名:  .l2s. + 任意文字 + .四位整数
            val interName = interWrongFile.name.takeIf {
                it.startsWith(interPrefix) && it.takeLast(4).matches(regex4Dec)  //中间文件名符合格式
            } ?: continue
        }

    }


}