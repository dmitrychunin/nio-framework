package ru.otus.framework.pipeline.handler;


import ru.otus.framework.RequestContext;

public interface ChannelHandler {
//    todo remove Object from return
    Object handle(RequestContext ctx, Object message);
}
