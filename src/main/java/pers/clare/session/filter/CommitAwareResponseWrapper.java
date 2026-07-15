package pers.clare.session.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import pers.clare.session.SyncSessionRequestContext;

import java.io.IOException;
import java.io.PrintWriter;

public class CommitAwareResponseWrapper extends HttpServletResponseWrapper {

    private final SyncSessionRequestContext<?> sessionContext;
    private boolean invoked = false;

    private PrintWriter writer;
    private ServletOutputStream outputStream;

    public CommitAwareResponseWrapper(HttpServletResponse response,
                                      SyncSessionRequestContext<?> sessionContext) {
        super(response);
        this.sessionContext = sessionContext;
    }

    private void beforeCommit() {
        if (!invoked) {
            invoked = true;
            sessionContext.refreshSession();
        }
    }

    public void refreshSessionIfNecessary() {
        beforeCommit();
    }

    @Override
    public void flushBuffer() throws IOException {
        beforeCommit();
        super.flushBuffer();
    }

    @Override
    public void sendError(int sc) throws IOException {
        beforeCommit();
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        beforeCommit();
        super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        beforeCommit();
        super.sendRedirect(location);
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(super.getWriter()) {
                @Override
                public void flush() {
                    beforeCommit();
                    super.flush();
                }

                @Override
                public void close() {
                    beforeCommit();
                    super.close();
                }
            };
        }
        return writer;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            ServletOutputStream delegate = super.getOutputStream();

            outputStream = new ServletOutputStream() {

                @Override
                public void write(int b) throws IOException {
                    delegate.write(b);
                }

                @Override
                public void flush() throws IOException {
                    beforeCommit();
                    delegate.flush();
                }

                @Override
                public void close() throws IOException {
                    beforeCommit();
                    delegate.close();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    delegate.setWriteListener(listener);
                }
            };
        }
        return outputStream;
    }
}
