package ru.otus.framework.el;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import ru.otus.framework.pipeline.ChannelPipeline;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Setter
@Getter
public class BossEventLoop implements EventLoop {
    private final int port;
    private final int workerThreadCount;
    private final List<WorkerEventLoop> workerList;
    private ExecutorService workerExecutor;

    public BossEventLoop(int port, int workerThreadCount) {
        this.port = port;
        this.workerThreadCount = workerThreadCount;
        this.workerList = new ArrayList<>(workerThreadCount);
    }

    @Override
    public void go(ChannelPipeline pipeline) {
        initWorkers(pipeline);
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
             Selector acceptSelector = Selector.open()) {
            serverSocketChannel.configureBlocking(false);
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(port));
            serverSocketChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
            acceptSelector.select();
            while (!Thread.currentThread().isInterrupted()) {

                try {
                    log.info("boss: listen new client connection");
                    SocketChannel socketChannel = serverSocketChannel.accept(); //The socket channel for the new connection
                    socketChannel.configureBlocking(false);
                    log.info("boss: new connection finded");
                    manageSmoothSocketAllocationOnWorkers(socketChannel);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initWorkers(ChannelPipeline pipeline) {
        workerExecutor = Executors.newFixedThreadPool(workerThreadCount);
        for (int i = 0; i < workerThreadCount; i++) {
            String workerName = "worker-" + i;
            WorkerEventLoop workerEventLoop = new WorkerEventLoop(workerName);
//          todo stop disconnected clients and remove corresponding worker socket???
            workerList.add(workerEventLoop);
            log.info("boss: submit new {} event loop", workerName);
            workerExecutor.submit(() -> workerEventLoop.go(pipeline));
        }
    }

    private void manageSmoothSocketAllocationOnWorkers(SocketChannel socketChannel) {
        log.info("boss: submit new socket");
        int index = 0;
        int minSize = Integer.MAX_VALUE;

        for (int i = 0; i < workerList.size(); i++) {
            WorkerEventLoop workerEventLoop = workerList.get(i);
            int size = workerEventLoop.getActiveSocketsCount();
            log.info("boss: {} already has {} sockets", workerEventLoop.getWorkerName(), size);
            if (size < minSize) {
                minSize = size;
                index = i;
            }
        }

        WorkerEventLoop workerEventLoop = workerList.get(index);
        workerEventLoop.registerSocket(socketChannel);
        log.info("accept new socket into {}", workerEventLoop.getWorkerName());
    }
}
