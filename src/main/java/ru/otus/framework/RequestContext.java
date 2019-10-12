package ru.otus.framework;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

@Getter
@Setter
public class RequestContext {
    private final URI uri;
    private final String httpRequestPayload;
    private final SocketChannel socket;
    private final String workerName;
    private boolean isSocketClosed = false;
    private Object payload;
    private String method;
    private Map<String, String> queryParamMap = new HashMap<>();
    private HTTP_CODES responseCode = HTTP_CODES.OK;

    public RequestContext(String rawHttpRequest, SocketChannel socket, String workerName) {
        this.socket = socket;
        this.workerName = workerName;
        String[] splittedHttpRequest = rawHttpRequest.split("\r\n");
        String[] s = splittedHttpRequest[0].split(" ");
        method = s[0];
        String[] split = s[1].split("\\?|&");
        String url = split[0];
        for (int i = 1; i < split.length; i++) {
            String s1 = split[i];
            String[] split1 = s1.split("=");
            queryParamMap.put(split1[0], split1[1]);
        }
//        for (String s1 : splittedHttpRequest) {
//           if (s1.startsWith("Content-Type")) {
//               contentType = s1.split("[:;]")[1].trim();
//           }
//        }
//        if (contentType == null) {
//            contentType = "plain/text";
//        }
        this.uri = URI.create(url);
        this.httpRequestPayload = splittedHttpRequest[splittedHttpRequest.length-1];
    }

    public void close() {
        writeResponsePayload(generateHttpResponse());
        isSocketClosed = true;
    }

//    todo add fireException method for exception propagation on pipeline
//    todo make private
    public void writeResponsePayload(String message) {
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

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String generateHttpResponse() {
        String payload = (String) this.payload;
        HTTP_CODES responseCode = this.responseCode;
        return format(
                "HTTP/1.1 %s %s\r\n" +
                        "Content-Type: plain/text\r\n" +
                        "Connection: Closed\r\n" +
                        "Content-Length: %s\r\n" +
                        "\r\n%s", responseCode.getCode(), responseCode.getName(), payload.length(), payload);
    }
}
