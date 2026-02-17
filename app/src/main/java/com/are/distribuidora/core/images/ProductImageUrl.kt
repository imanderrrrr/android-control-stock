package com.are.distribuidora.core.images

object ProductImageUrl {
    private const val LOCAL_PREFIX = "local://"

    fun isLocal(url: String?): Boolean = url?.startsWith(LOCAL_PREFIX) == true

    fun isRemoteHttp(url: String?): Boolean =
        url?.startsWith("http://") == true || url?.startsWith("https://") == true

    fun toLocalUrl(absolutePath: String): String = "$LOCAL_PREFIX$absolutePath"

    fun localPathOrNull(url: String?): String? {
        if (!isLocal(url)) return null
        return url!!.removePrefix(LOCAL_PREFIX)
    }
}
