package ru.otus.framework;

import ru.otus.framework.pipeline.ChannelPipeline;

import java.util.Optional;

public class Router {
    private String path;
    private final ChannelPipeline channelPipeline;

    private Router(ChannelPipeline channelPipeline){
        this.channelPipeline = channelPipeline;
    }

    public static Router route(ChannelPipeline channelPipeline) {
        return new Router(channelPipeline);
    }

    public Router path(String path) {
        this.path = path;
        return this;
    }

    public Optional<ChannelPipeline> getPipeline(RequestContext requestContext) {
        if (path != null && requestContext.getUri().toString().contains(path)) {
            return Optional.of(channelPipeline);
        }
        return Optional.empty();
    }
}
