package ru.otus.framework.el

import ru.otus.framework.Router
import java.util.concurrent.Executors

class ServerBootstrap(
        private val routers: List<Router>,
        private val port: Int,
        private val workerThreadCount: Int) {

    fun startServer() {
        val bossExecutor = Executors.newSingleThreadExecutor()
        val bossEventLoop = BossEventLoop(port, workerThreadCount, routers)
        bossExecutor.submit { bossEventLoop.go() }
    }
}