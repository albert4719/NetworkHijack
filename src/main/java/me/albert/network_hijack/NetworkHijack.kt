package me.albert.network_hijack

import me.albert.network_hijack.interceptor.NetworkInterceptorFactory
import me.albert.network_hijack.utils.CustomConfig
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Field
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

lateinit var instance: NetworkHijack
val monitorPluginName get() = instance.config.getString("current-plugin") ?: ""
var monitorConfig: CustomConfig? = null

class NetworkHijack : JavaPlugin() {
    private lateinit var logFile: File
    private val logger: Logger = Logger.getLogger("NetworkHijack")

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        setupLogFile()
        if (monitorPluginName.isNotEmpty()) {
            monitorConfig = CustomConfig("data${File.separatorChar}${monitorPluginName}.yml", this)
        }
        resetURLStreamHandlerFactory()
        setupNetworkInterceptor()
        log("NetworkHijack 已启用")
    }

    override fun onDisable() {
        log("NetworkHijack disabled.")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>?
    ): MutableList<String>? {
        // args 为空或长度为 0 时，补全 start 和 stop
        if (args == null || args.isEmpty()) {
            return mutableListOf("start", "stop")
        }
        // 根据 args 长度处理补全
        when (args.size) {
            1 -> {
                // 输入 /nh <tab>，补全 start 和 stop
                return mutableListOf("start", "stop", "reload").filter {
                    it.startsWith(args[0], ignoreCase = true)
                }.toMutableList()
            }

            2 -> {
                // 输入 /nh start <tab>，补全插件名字
                if (args[0].equals("start", ignoreCase = true)) {
                    // 获取服务器加载的插件名字
                    val plugins = Bukkit.getPluginManager().plugins
                    return plugins.map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                        .toMutableList()
                }
            }
        }
        return null
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (args?.size == 2 && args[0].equals("start", ignoreCase = true)) {
            val pluginName = args[1].lowercase()
            monitorConfig = CustomConfig("data${File.separatorChar}${pluginName}.yml", this)
            instance.config.set("current-plugin", pluginName)
            saveConfig()
            sender.sendMessage("§b已开始监控插件${monitorPluginName}的请求")
            return true
        }
        if (args?.size == 1 && args[0].equals("stop", ignoreCase = true)) {
            if (monitorPluginName.isEmpty()) {
                sender.sendMessage("§b请先输入start开始记录请求")
                return true
            }
            monitorConfig?.save()
            config.set("current-plugin", "")
            saveConfig()
            sender.sendMessage("§b数据已保存")
            return true
        }
        if (args?.size == 1 && args[0].equals("reload", ignoreCase = true)) {
            reloadConfig()
            sender.sendMessage("§b已重新载入配置文件")
            return true
        }
        sender.sendMessage("§7[§b帮助§7]")
        sender.sendMessage("§b/nh start [插件名字] 开始记录")
        sender.sendMessage("§b/nh stop 停止记录并写入")
        sender.sendMessage("§b/nh reload 重新载入配置文件")
        return true
    }

    private fun resetURLStreamHandlerFactory() {
        try {
            val factoryField: Field = URL::class.java.getDeclaredField("factory")
            factoryField.isAccessible = true
            factoryField.set(null, null)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupLogFile() {
        val logDir = File(dataFolder, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        logFile = File(logDir, "network_log_$timestamp.txt")
        if (!logFile.exists()) logFile.createNewFile()
    }

    private fun setupNetworkInterceptor() {
        try {
            // Set custom URLStreamHandlerFactory
            URL.setURLStreamHandlerFactory(NetworkInterceptorFactory(this))
            log("Custom URLStreamHandlerFactory installed.")
        } catch (e: Exception) {
            log("Failed to set URLStreamHandlerFactory: ${e.message}")
        }
    }

    fun log(message: String) {
        logger.info(message)
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val logMessage = "[$timestamp] $message\n"
            Files.write(logFile.toPath(), logMessage.toByteArray(), StandardOpenOption.APPEND)
        } catch (e: Exception) {
            logger.severe("Failed to write to log file: ${e.message}")
        }
    }

    fun getCallingPlugin(): Plugin? {
        val stackTrace = Thread.currentThread().stackTrace
        val plugins = server.pluginManager.plugins
        for (element in stackTrace) {
            try {
                val clazz = Class.forName(element.className)
                for (plugin in plugins) {
                    if (plugin.javaClass.classLoader == clazz.classLoader && plugin != this) {
                        return plugin
                    }
                }
            } catch (e: ClassNotFoundException) {
                continue
            }
        }
        return null
    }
}





