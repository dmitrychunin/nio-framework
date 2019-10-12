package integration.handler;

import lombok.extern.slf4j.Slf4j;
import ru.otus.framework.RequestContext;
import ru.otus.framework.pipeline.handler.ChannelHandler;

@Slf4j
public class PackagePayloadWithWorkerNameHandler implements ChannelHandler {
    @Override
    public Object handle(RequestContext ctx, Object message) {
        return ctx.getWorkerName() + ": echo: " + message;
    }

    @Override
    public void exceptionCaught(RequestContext ctx, Throwable cause) {
        log.error("Ошибка при замене добавлении префикса worker'а обрабатывающего сообщение");
        ctx.close();
    }
}
