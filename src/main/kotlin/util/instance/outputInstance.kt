package com.magnariuk.util.instance

import com.magnariuk.util.Table
import com.magnariuk.util.t

fun outputInstance(name: String) {
    val instance = getInstance(name)!!

    val table = Table(t("instance.table.title", listOf(name)), listOf())

    table.printVertical(
        mapOf(
            t("instance.table.version") to instance.version.minecraft,
            t("instance.table.memory") to instance.memory,
            t("instance.table.loader") to "${instance.version.loader.type}:${instance.version.loader.version}",
            t("instance.table.ab") to instance.autoBackup,
            t("instance.table.rp") to instance.resourcepack,
        )
    )

}