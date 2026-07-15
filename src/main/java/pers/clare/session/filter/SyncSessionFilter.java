package pers.clare.session.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import pers.clare.session.SyncSessionRequestContext;
import pers.clare.session.SyncSessionRequestContextHolder;
import pers.clare.session.listener.SessionAsyncListener;
import pers.clare.session.service.SessionIdTransportService;
import pers.clare.session.service.SyncSessionService;

import java.io.IOException;


@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncSessionFilter extends OncePerRequestFilter {

    @Setter(onMethod = @__(@Autowired))
    private SyncSessionService<?> syncSessionService;

    @Setter(onMethod = @__(@Autowired))
    private SessionIdTransportService sessionIdTransportService;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        @SuppressWarnings("unused")
        SyncSessionRequestContext<?> sessionContext = SyncSessionRequestContextHolder.init(request, response, syncSessionService, sessionIdTransportService);

        var responseWrapper = new CommitAwareResponseWrapper(response, sessionContext);
        try {
            filterChain.doFilter(request, responseWrapper);
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new SessionAsyncListener(sessionContext));
            } else {
                responseWrapper.refreshSessionIfNecessary();
            }
        } finally {
            SyncSessionRequestContextHolder.clear();
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
