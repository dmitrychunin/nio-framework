package ru.otus.framework.pipeline;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import ru.otus.framework.RequestContext;
import ru.otus.framework.pipeline.handler.ChannelHandler;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Value
public class ChannelPipeline {
    private final List<ChannelHandler> pipelineHandlerOrder = new ArrayList<>();

    public void addLast(ChannelHandler handler) {
        pipelineHandlerOrder.add(handler);
    }
    public RequestContext start(RequestContext context, Object message) {
        for (ChannelHandler channelHandler : pipelineHandlerOrder) {
            message = channelHandler.handle(context, message);
        }
        context.setPayload(message);
        return context;
    }
}
