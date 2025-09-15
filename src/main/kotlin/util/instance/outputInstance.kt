package com.magnariuk.util.instance

import com.magnariuk.util.Table

fun outputInstance(name: String) {
    val instance = getInstance(name)!!

    val table = Table("Instance $name", listOf())

    table.printVertical(
        mapOf(
            "Version" to instance.version.minecraft,
            "Memory" to instance.memory,
            "Loader" to "${instance.version.loader.type}:${instance.version.loader.version}",
            "Auto-Backup" to instance.autoBackup,
            "Resourcepack" to instance.resourcepack,
            "Resourcepack port" to instance.resourcepackPort
        )
    )

}