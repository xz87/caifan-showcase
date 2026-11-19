package com.caifan.admin.mall;

import com.caifan.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 小程序与 Android 客户端登录拦截器。保护 /api/mall/**，校验 Authorization Bearer Token。
 */
@Component
@RequiredArgsConstructor
public class MallAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final MallTokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 小程序使用 Bearer Token；解析成功后写入请求属性，Controller 仍走 MallSession.current
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            MallCustomer tokenCustomer = tokenService.resolve(authorization.substring(7).trim());
            if (tokenCustomer != null) {
                request.setAttribute(MallSession.TOKEN_KEY, tokenCustomer);
            }
        }
        MallCustomer customer = MallSession.current(request);
        if (customer != null && request.getAttribute(MallSession.TOKEN_KEY) != null) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(2001, "请先登录")));
        return false;
    }
}
