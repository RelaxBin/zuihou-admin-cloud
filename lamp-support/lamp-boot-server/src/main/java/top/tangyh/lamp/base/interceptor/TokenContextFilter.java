package top.tangyh.lamp.base.interceptor;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import top.tangyh.basic.context.ContextConstants;
import top.tangyh.basic.context.ContextUtil;
import top.tangyh.basic.utils.StrPool;
import top.tangyh.lamp.common.properties.IgnoreProperties;
import top.tangyh.lamp.common.utils.Base64Util;

import static top.tangyh.basic.context.ContextConstants.APPLICATION_ID_HEADER;
import static top.tangyh.basic.context.ContextConstants.APPLICATION_ID_KEY;
import static top.tangyh.basic.context.ContextConstants.CLIENT_KEY;

/**
 * 用户信息解析器 一定要在AuthenticationFilter之前执行
 *
 * @author tangyh
 * @version v1.0
 * @date 2021/12/28 2:36 下午
 * @create [2021/12/28 2:36 下午 ] [tangyh] [初始创建]
 */
@Slf4j
@RequiredArgsConstructor
public class TokenContextFilter implements AsyncHandlerInterceptor {
    private final String profiles;
    private final IgnoreProperties ignoreProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            log.debug("not exec!!! url={}", request.getRequestURL());
            return true;
        }
        ContextUtil.setBoot(true);
        ContextUtil.setPath(getHeader(ContextConstants.PATH_HEADER, request));
        String traceId = IdUtil.fastSimpleUUID();
        MDC.put(ContextConstants.TRACE_ID_HEADER, traceId);
        try {
            // 1,解码 Authorization
            parseClient(request);

            // 2, 获取 应用id
            parseApplication(request);


        } catch (Exception e) {
            log.error("request={}", request.getRequestURL(), e);
            throw e;
        }

        return true;
    }


    private void parseClient(HttpServletRequest request) {
        String base64Authorization = getHeader(CLIENT_KEY, request);
        if (StrUtil.isNotEmpty(base64Authorization)) {
            String[] client = Base64Util.getClient(base64Authorization);
            ContextUtil.setClientId(client[0]);
        }
    }

    private void parseApplication(HttpServletRequest request) {
        String applicationIdStr = getHeader(APPLICATION_ID_KEY, request);
        if (StrUtil.isNotEmpty(applicationIdStr)) {
            ContextUtil.setApplicationId(applicationIdStr);
            MDC.put(APPLICATION_ID_HEADER, applicationIdStr);
        }
    }


    private String getHeader(String name, HttpServletRequest request) {
        String value = request.getHeader(name);
        if (StrUtil.isEmpty(value)) {
            value = request.getParameter(name);
        }
        if (StrUtil.isEmpty(value)) {
            return null;
        }
        return URLUtil.decode(value);
    }


    protected boolean isDev(String token) {
        return !StrPool.PROD.equalsIgnoreCase(profiles) && (StrPool.TEST_TOKEN.equalsIgnoreCase(token) || StrPool.TEST.equalsIgnoreCase(token));
    }

    /**
     * 忽略应用级token
     *
     * @return
     */
    protected boolean isIgnoreToken(HttpServletRequest request) {
        return ignoreProperties.isIgnoreUser(request.getMethod(), request.getRequestURI());
    }

    /**
     * 忽略 租户编码
     *
     * @return
     */
    protected boolean isIgnoreTenant(HttpServletRequest request) {
        return ignoreProperties.isIgnoreTenant(request.getMethod(), request.getRequestURI());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ContextUtil.remove();
    }
}
