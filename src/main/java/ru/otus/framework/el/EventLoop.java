package ru.otus.framework.el;


import ru.otus.framework.pipeline.ChannelPipeline;

public interface EventLoop {
    void go(ChannelPipeline channelPipeline);
}
