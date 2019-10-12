package ru.otus.framework.pipeline.handler;


import ru.otus.framework.RequestContext;

public interface ChannelHandler {
    Object handle(RequestContext ctx, Object message);

    void exceptionCaught(RequestContext ctx, Throwable cause);
}
