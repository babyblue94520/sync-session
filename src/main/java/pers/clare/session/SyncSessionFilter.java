package pers.clare.session;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;



@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncSessionFilter implements Filter {

    private final SyncSessionService<?> syncSessionService;

    public SyncSessionFilter(SyncSessionService<?> syncSessionService) {
        this.syncSessionService = syncSessionService;
    }

    @Override
    public void doFilter(
            ServletRequest req
            , ServletResponse res
            , FilterChain chain
    ) throws IOException, ServletException {
        @SuppressWarnings("unused")
        RequestCache<?> requestCache = RequestCacheHolder.init((HttpServletRequest) req, (HttpServletResponse) res, syncSessionService);
        try {
            chain.doFilter(req, res);
            if(req.isAsyncStarted()){
                req.getAsyncContext().addListener(new SessionAsyncListener(requestCache));
            }
        } finally {
            if(!req.isAsyncStarted()){
                requestCache.refreshSession();
            }
            RequestCacheHolder.clear();
        }
    }

}
