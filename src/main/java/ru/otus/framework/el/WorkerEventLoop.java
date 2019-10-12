package ru.otus.framework.el;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import ru.otus.framework.RequestContext;
import ru.otus.framework.Router;
import ru.otus.framework.pipeline.ChannelPipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Data
class WorkerEventLoop implements EventLoop {
    private final String workerName;
    private final ExecutorService asyncPipelineExecutor = Executors.newSingleThreadExecutor();
    private final List<Router> routers;
    private Selector readSelector;
    private int activeSocketsCount;
    private List<Future<RequestContext>> resultList = new ArrayList<>();

    public void registerSocket(SocketChannel socketChannel) {
        activeSocketsCount++;
        try {
            socketChannel.register(readSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void go() {
        try (Selector readSelector = Selector.open()) {
//            todo refactor
            this.readSelector = readSelector;
            while (!Thread.currentThread().isInterrupted()) {
                syncActiveSocketsCount();
                log.info("{}: listen new ready clients", workerName);
//                todo почему без selectedNow не работает???
                readSelector.selectNow();
                Iterator<SelectionKey> readKeys = readSelector.selectedKeys().iterator();
                while (readKeys.hasNext()) {
                    SelectionKey key = readKeys.next();
                    log.info("{} handle key {}", workerName, key.interestOps());
                    if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        String socketPayload = readRequestPayload(channel);
                        RequestContext requestContext = new RequestContext(socketPayload, channel, workerName);
                        ChannelPipeline pipeline = selectFirstMatchingRouterAndPipeline(requestContext);
                        Future<RequestContext> submit = asyncPipelineExecutor.submit(() -> pipeline.start(requestContext, requestContext.getHttpRequestPayload()));
                        resultList.add(submit);
                    } else if (key.isWritable()) {
                        handleWriteSocketEvent(key);
                    } else {
                        throw new RuntimeException(workerName + ": key is not readable");
                    }
                    readKeys.remove();
                }
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
            RuntimeException runtimeException = new RuntimeException("Ошибка в worker event loop " + workerName);
            runtimeException.initCause(e);
            throw runtimeException;
        }
    }

    private ChannelPipeline selectFirstMatchingRouterAndPipeline(RequestContext requestContext) {
        Optional<ChannelPipeline> firstMatchedPipeline = routers.stream()
                .map(router -> router.getPipeline(requestContext))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        return firstMatchedPipeline.orElseThrow(RuntimeException::new);
    }

    public String readRequestPayload(SocketChannel socket) {
        try {
            log.info("{}: readRequestPayload from client", workerName);
            ByteBuffer buffer = ByteBuffer.allocate(5);
            StringBuilder inputBuffer = new StringBuilder(100);
            while (socket.read(buffer) > 0) {
                buffer.flip();
                String input = Charset.forName("UTF-8").decode(buffer).toString();
                log.info("{}: from client: {} ", workerName, input);

                buffer.flip();
                buffer.clear();
                inputBuffer.append(input);
            }
            return inputBuffer.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new RuntimeException();
    }

    private void handleWriteSocketEvent(SelectionKey key) throws ExecutionException, InterruptedException {
        SelectableChannel channel = key.channel();

        for (int i = 0; i < resultList.size(); i++) {
            if (!resultList.get(i).isDone()) {
                continue;
            }
            RequestContext context = resultList.get(i).get();
            SocketChannel socket = context.getSocket();

            if (socket == channel) {
                String httpResponse = context.generateHttpResponse();
                context.writeResponsePayload(httpResponse);
                resultList.remove(i);
            }
        }
    }

    private void syncActiveSocketsCount() {
        for (Future<RequestContext> booleanFuture : resultList) {
            if (!booleanFuture.isDone()) {
                continue;
            }
            if (isSocketClosed(booleanFuture)) {
                activeSocketsCount--;
            }
        }
    }

    private boolean isSocketClosed(Future<RequestContext> contextFuture) {
        try {
            return contextFuture.get().isSocketClosed();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка при проверке закрыт ли сокет");
        }
    }
}
