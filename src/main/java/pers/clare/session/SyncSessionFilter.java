package pers.clare.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncSessionFilter implements Filter {
    private static final Logger log = LogManager.getLogger();

    private final SyncSessionService<SyncSession> syncSessionService;

    public SyncSessionFilter(SyncSessionService<SyncSession> syncSessionService) {
        this.syncSessionService = syncSessionService;
    }

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
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        RequestCache<SyncSession> requestCache = RequestCacheHolder.init(request, response, syncSessionService);
        try {
            chain.doFilter(request, response);
            requestCache.refreshSession();
        } finally {
            requestCache.finish();
        }
    }
}
