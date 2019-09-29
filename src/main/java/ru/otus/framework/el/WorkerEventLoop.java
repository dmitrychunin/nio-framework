package ru.otus.framework.el;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import ru.otus.framework.RequestContext;
import ru.otus.framework.pipeline.ChannelPipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.String.format;

@Slf4j
@Data
public class WorkerEventLoop implements EventLoop {
    //    todo make final and @value
    private final String workerName;
    private final ExecutorService asyncPipelineExecutor = Executors.newSingleThreadExecutor();
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
    public void go(ChannelPipeline pipeline) {
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
                        //            todo refactor
                        if (isClientSendStopRequest(socketPayload)) {
                            closeSocketAndSendResponse(channel, socketPayload);
                            continue;
                        }
                        Future<RequestContext> submit = asyncPipelineExecutor.submit(() -> pipeline.start(requestContext, requestContext.getHttpRequestPayload()));
                        resultList.add(submit);
                    } else if (key.isWritable()) {
                        handleWriteSocketEvent(key);
                    } else {
                        throw new RuntimeException(workerName + ": key is not readable");
                    }
                    readKeys.remove();
                }
                sleepOneSecond();
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
            RuntimeException runtimeException = new RuntimeException("Ошибка в worker event loop " + workerName);
            runtimeException.initCause(e);
            throw runtimeException;
        }
    }

    public void writeResponsePayload(SocketChannel socket, String message) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(5);
            byte[] response = message.getBytes();
            for (byte b : response) {
                buffer.put(b);
                if (buffer.position() == buffer.limit()) {
                    buffer.flip();
                    socket.write(buffer);
                    buffer.flip();
                    buffer.clear();
                }
            }
            if (buffer.hasRemaining()) {
                buffer.flip();
                socket.write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
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
                String httpResponse = generateHttpResponse(context);
                writeResponsePayload((SocketChannel) channel, httpResponse);
                resultList.remove(i);
            }
        }
    }

    private String generateHttpResponse(RequestContext requestContext) {
        String payload = (String) requestContext.getPayload();
        return format(
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: plain/text\r\n" +
                        "Connection: Closed\r\n" +
                        "Content-Length: %s\r\n" +
                        "\r\n%s", payload.length(), payload);
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean isClientSendStopRequest(String requestPayload) {
        return "stop\n".equals(requestPayload);
    }

    //    todo refactor use another pipeline?
    private void closeSocketAndSendResponse(SocketChannel channel, String requestPayload) {
        log.info("channel closed by client request");
        writeResponsePayload(channel, requestPayload);
        try {
            channel.close();
//            todo what if close fails?
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void syncActiveSocketsCount() {
        for (int i = 0; i < resultList.size(); i++) {
            Future<RequestContext> booleanFuture = resultList.get(i);
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
