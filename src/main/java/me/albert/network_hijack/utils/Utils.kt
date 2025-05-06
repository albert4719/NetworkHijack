package me.albert.network_hijack.utils

import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin

fun PluginManager.getPluginFromClass(clazz: Class<*>): JavaPlugin? {
    for (plugin in this.plugins) {
        if (plugin is JavaPlugin && plugin.javaClass.classLoader == clazz.classLoader) {
            return plugin
        }
    }
    return null
}