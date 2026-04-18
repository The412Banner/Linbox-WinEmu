package org.github.ewt45.winemulator.emu

import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.github.ewt45.winemulator.Consts
import java.io.File
import java.nio.charset.StandardCharsets


class ProotRootfs {
    data class UserInfo(
        val name: String,
        val uid: Long,
        val gid: Long,
        val home: String,
        val shell: String,
    ) {
        companion object {
            val ROOT = UserInfo("root", 0L, 0L, "/root", "/bin/sh")
        }
    }


    companion object {
        /** Reads user entries from /etc/passwd inside a rootfs directory, sorted alphabetically by username. */
        fun getUserInfos(rootfs: File): List<UserInfo> {
            var returnValue:List<UserInfo>
            try {
                returnValue = FileUtils.readLines(File(rootfs, "/etc/passwd"), StandardCharsets.UTF_8).mapNotNull { line ->
                    line.split(":").takeIf { it.size == 7 }?.let {
                        val uid = it[2].toLong()
                        if (uid < 1000L && uid != 0L) return@let null
                        if (uid == 65534L) return@let null
                        return@let UserInfo(it[0], uid, it[3].toLong(), it[5], it[6])
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                returnValue = listOf()
            }
            if (returnValue.find { it.name == "root" } == null)
                returnValue += (UserInfo.ROOT)
            // proot-distro adds Termux as a user; it's unusable in other apps so we hide it
            return returnValue.filter { !it.name.startsWith("aid_") }.sortedBy { it.name }
        }

        /** Returns the currently selected user folder name from /.emuconf. */
        fun getCurrentSelectUser(name:String) {
            TODO("Currently stored in DataStore (JSON)")
        }

        /**
         * Like [getPreferredUser](String?, List) but looks up the stored username via [rootfsName].
         * @param rootfsName key in [Consts.Pref.Local.rootfs_login_user_json]; must not be "current"
         */
        suspend fun getPreferredUser(rootfsName: String):UserInfo  {
            if (rootfsName == Consts.rootfsCurrDir.name) throw IllegalArgumentException("rootfsName used for lookup must not be 'current'")
            val userMap: Map<String, String> = Json.decodeFromString(Consts.Pref.Local.rootfs_login_user_json.get())
            return getPreferredUser(userMap[rootfsName], getUserInfos(File(Consts.rootfsAllDir,rootfsName)))
        }

        /**
         * Returns the preferred login user for a rootfs.
         * Reads from the locally stored JSON first; falls back to the first non-root user, then root.
         * @param lastSelectedUserName locally stored default username for this rootfs; may be null if never set
         * @param allUsers all available users in this rootfs
         */
        fun getPreferredUser(lastSelectedUserName: String?, allUsers: List<UserInfo>): UserInfo = allUsers.run {
            var foundInfo = find { info -> info.name == lastSelectedUserName }
            if (foundInfo == null) foundInfo = find { info -> info.name != "root" }
            if (foundInfo == null) foundInfo = find { info -> info.name == "root" }
            if (foundInfo == null) foundInfo = UserInfo.ROOT
            return foundInfo
        }
    }
}