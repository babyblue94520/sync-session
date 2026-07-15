package pers.clare.session.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface SessionIdTransportService {

    String find(HttpServletRequest request);

    void write(HttpServletRequest request, HttpServletResponse response, String sessionId);

    void clear(HttpServletRequest request, HttpServletResponse response);
}

