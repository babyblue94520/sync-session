package pers.clare.session;

import org.junit.jupiter.api.TestInstance;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLResponse;

import java.util.List;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class SessionHeaderTransportTest extends AbstractSingleNodeTransportTest {

    private String sessionId;

    @Override
    protected String[] extraArgs() {
        return new String[]{"--sync-session.mode=header"};
    }

    @Override
    protected void resetClientState() {
        sessionId = null;
    }

    @Override
    protected URLRequest<String> applySession(URLRequest<String> request) {
        if (sessionId != null) {
            request.header("SSESSIONID", sessionId);
        }
        return request;
    }

    @Override
    protected void captureSession(URLResponse<String> response) {
        List<String> values = response.getHeaders().get("SSESSIONID");
        if (values != null && !values.isEmpty()) {
            sessionId = values.get(0);
        }
    }

    @Override
    protected void storeSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
