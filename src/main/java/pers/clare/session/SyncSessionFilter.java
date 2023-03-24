package pers.clare.session;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;



@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncSessionFilter extends OncePerRequestFilter {

    private final SyncSessionService<?> syncSessionService;

    public SyncSessionFilter(SyncSessionService<?> syncSessionService) {
        this.syncSessionService = syncSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        @SuppressWarnings("unused")
        RequestCache<?> requestCache = RequestCacheHolder.init(request, response, syncSessionService);
        try {
            filterChain.doFilter(request, response);
            if(request.isAsyncStarted()){
                request.getAsyncContext().addListener(new SessionAsyncListener(requestCache));
            }
        } finally {
            if(!request.isAsyncStarted()){
                requestCache.refreshSession();
            }
            RequestCacheHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
