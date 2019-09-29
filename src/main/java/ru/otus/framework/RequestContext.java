package ru.otus.framework;

import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.nio.channels.SocketChannel;

@Getter
@Setter
public class RequestContext {
//    todo add params?
    private final URI uri;
    private final String httpRequestPayload;
    private final SocketChannel socket;
    private final String workerName;
    private boolean isSocketClosed = false;
    private Object payload;

    public RequestContext(String rawHttpRequest, SocketChannel socket, String workerName) {
        this.socket = socket;
        this.workerName = workerName;
        String[] splittedHttpRequest = rawHttpRequest.split("\r\n");
        String[] s = splittedHttpRequest[0].split(" ");
        String method = s[0];
        String url = s[1];
//        todo parse query params
        this.uri = URI.create(url);
        this.httpRequestPayload = splittedHttpRequest[splittedHttpRequest.length-1];
    }
}
