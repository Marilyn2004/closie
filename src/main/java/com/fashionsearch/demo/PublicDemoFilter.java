package com.fashionsearch.demo;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-100)
public class PublicDemoFilter extends OncePerRequestFilter {
  private final PublicDemoPolicy policy;

  public PublicDemoFilter(java.util.Optional<PublicDemoPolicy> policy) {
    this.policy = policy.orElse(null);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (policy == null || !policy.enabled()) {
      chain.doFilter(req, res);
      return;
    }
    String p = req.getServletPath(), method = req.getMethod();
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("X-Frame-Options", "DENY");
    res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    res.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"
            + " https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src"
            + " 'self' blob: data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self';"
            + " form-action 'self'");
    if (p.startsWith("/api/")) res.setHeader("Cache-Control", "no-store");
    boolean read = method.equals("GET") || method.equals("HEAD");
    boolean allowed =
        read
            && (p.equals("/")
                || p.equals("/favicon.svg")
                || p.matches("/assets/[a-zA-Z0-9._-]+")
                || p.matches("/concepts/[a-z-]+\\.(png|jpg)")
                || p.equals("/api/status")
                || p.matches("/api/studio/(status|discover|items|preferences|jobs|uploads)")
                || p.matches("/api/studio/items/[a-f0-9-]{36}"));
    allowed |=
        method.equals("POST")
            && (p.equals("/api/studio/find")
                || p.equals("/api/studio/outfits")
                || p.matches("/api/studio/outfits/[a-f0-9-]{36}/replace")
                || p.matches("/api/studio/items/[a-f0-9-]{36}/revert"));
    allowed |= method.equals("PUT") && p.equals("/api/studio/preferences");
    allowed |= method.equals("DELETE") && p.matches("/api/studio/items/[a-f0-9-]{36}");
    if (!allowed || p.contains("..")) {
      res.setStatus(403);
      res.setContentType("application/json");
      res.getWriter()
          .write(
              "{\"message\":\"This public demo searches and remixes pre-generated concepts. Live"
                  + " generation, private uploads, retail lookup and administrative APIs are"
                  + " disabled.\"}");
      return;
    }
    if (req.getContentLengthLong() > 16384) {
      res.setStatus(413);
      res.setContentType("application/json");
      res.getWriter().write("{\"message\":\"Public demo requests must be under 16 KB.\"}");
      return;
    }
    if (method.equals("POST") || method.equals("PUT")) req = bounded(req);
    chain.doFilter(req, res);
  }

  private HttpServletRequest bounded(HttpServletRequest original) throws IOException {
    var source = original.getInputStream();
    var limited =
        new ServletInputStream() {
          int count;

          private int track(int size) throws IOException {
            if (size > 0 && (count += size) > 16384)
              throw new IOException("Public demo request too large.");
            return size;
          }

          public int read() throws IOException {
            int value = source.read();
            if (value >= 0) track(1);
            return value;
          }

          public int read(byte[] bytes, int offset, int length) throws IOException {
            return track(source.read(bytes, offset, Math.min(length, 16385 - count)));
          }

          public boolean isFinished() {
            return source.isFinished();
          }

          public boolean isReady() {
            return source.isReady();
          }

          public void setReadListener(ReadListener listener) {
            source.setReadListener(listener);
          }
        };
    return new HttpServletRequestWrapper(original) {
      @Override
      public ServletInputStream getInputStream() {
        return limited;
      }

      @Override
      public java.io.BufferedReader getReader() {
        return new java.io.BufferedReader(
            new java.io.InputStreamReader(limited, java.nio.charset.StandardCharsets.UTF_8));
      }
    };
  }
}
