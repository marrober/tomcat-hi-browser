package com.redhat.clock;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@WebServlet("/api/time")
public class TimeServlet extends HttpServlet {
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ZonedDateTime now = ZonedDateTime.now();
        int millisIntoSecond = now.getNano() / 1_000_000;
        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setIntHeader("X-Delay-Millis", Math.max(1, 1000 - millisIntoSecond));
        response.getWriter().write(CLOCK.format(now));
    }
}
