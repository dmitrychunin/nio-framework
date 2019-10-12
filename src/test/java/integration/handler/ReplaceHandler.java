package integration.handler;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.otus.framework.HTTP_CODES;
import ru.otus.framework.RequestContext;
import ru.otus.framework.pipeline.handler.ChannelHandler;

import java.util.Map;

@Slf4j
public class ReplaceHandler implements ChannelHandler {
    @Override
    public Object handle(RequestContext ctx, Object message) {
        log.info("{}: before replace: {} ", ctx.getWorkerName(), message);

        Map<String, String> queryParamMap = ctx.getQueryParamMap();
        String beforeReplace = queryParamMap.get("beforeReplace");
        String afterReplace = queryParamMap.get("afterReplace");
        checkQueryParams(beforeReplace, afterReplace);

        message = StringUtils.replaceIgnoreCase((String) message, beforeReplace, afterReplace);
        log.info("{}: after replace: {}", ctx.getWorkerName(), message);
        return message;
    }

    @Override
    public void exceptionCaught(RequestContext ctx, Throwable cause) {
        log.error("Ошибка при замене beforeReplace на afterReplace");
        ctx.setPayload("Error on replacing beforeReplace to afterReplace");
        ctx.setResponseCode(HTTP_CODES.BAD_REQUEST);
        ctx.close();
    }

    private void checkQueryParams(@NonNull String beforeReplace, @NonNull String afterReplace) {
    }
}
