package pers.clare.session;

import org.junit.jupiter.api.TestInstance;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLResponse;

import java.net.CookieManager;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class SessionCookieTransportTest extends AbstractSingleNodeTransportTest {

    private CookieManager cookieManager;
    private String sessionId;

    @Override
    protected String[] extraArgs() {
        return new String[0];
    }

    @Override
    protected void resetClientState() {
        cookieManager = new CookieManager();
        sessionId = null;
    }

    @Override
    protected URLRequest<String> applySession(URLRequest<String> request) {
        request.cookieManager(cookieManager);
        if (sessionId != null) {
            request.header("Cookie", "SSESSIONID=" + sessionId);
        }
        return request;
    }

    @Override
    protected void captureSession(URLResponse<String> response) {
    }

    @Override
    protected void storeSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
