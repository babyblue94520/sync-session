package com.primestar.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncSessionFilter implements Filter {
    private static final Logger log = LogManager.getLogger();

    @Autowired
    private SyncSessionService<?> syncSessionService;

    @Override
    public void init(FilterConfig filterConfig) {
        log.info(getClass().getSimpleName() + " startup");
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(
            ServletRequest req
            , ServletResponse res
            , FilterChain chain
    ) throws IOException, ServletException {
        RequestCache<?> requestCache = RequestCacheHolder.init((HttpServletRequest) req, (HttpServletResponse) res, syncSessionService);
        try {
            chain.doFilter(req, res);
        } finally {
            requestCache.refreshSession();
        }
    }
}
