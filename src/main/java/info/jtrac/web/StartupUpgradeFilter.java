package info.jtrac.web;

import info.jtrac.Jtrac;
import info.jtrac.domain.BatchInfo;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Gatekeeper filter that displays a friendly live-progress upgrade notice page while
 * JTrac NG is performing an automatic startup Lucene index upgrade from a legacy version,
 * and automatically redirects visitors to the login page as soon as the upgrade finishes.
 */
public class StartupUpgradeFilter implements Filter {

    public static final String STATUS_PATH = "/startup-upgrade-status";

    private ServletContext servletContext;
    private volatile Jtrac jtrac;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (filterConfig != null) {
            this.servletContext = filterConfig.getServletContext();
        }
    }

    @Override
    public void destroy() {
    }

    /**
     * Package-private hook for unit tests.
     */
    void setJtrac(Jtrac jtrac) {
        this.jtrac = jtrac;
    }

    private Jtrac resolveJtrac() {
        Jtrac ref = this.jtrac;
        if (ref != null) {
            return ref;
        }
        if (servletContext != null) {
            ApplicationContext ctx = WebApplicationContextUtils.getWebApplicationContext(servletContext);
            if (ctx != null && ctx.containsBean("jtrac")) {
                ref = (Jtrac) ctx.getBean("jtrac");
                this.jtrac = ref;
            }
        }
        return ref;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String contextPath = req.getContextPath() != null ? req.getContextPath() : "";
        String uri = req.getRequestURI() != null ? req.getRequestURI() : "";
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

        Jtrac svc = resolveJtrac();

        // Lightweight JSON polling endpoint (always available so upgrade page detects completion cleanly)
        if (STATUS_PATH.equals(path)) {
            writeStatusJson(res, svc);
            return;
        }

        // Fast path: zero overhead once startup upgrade is complete
        if (svc == null || !svc.isStartupUpgradeInProgress()) {
            chain.doFilter(request, response);
            return;
        }

        // Allow static assets (logo, icons, CSS) so the upgrade notice renders cleanly
        if (path.startsWith("/resources/") || "/favicon.ico".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        writeUpgradeNoticePage(res, contextPath, svc);
    }

    private void writeStatusJson(HttpServletResponse res, Jtrac svc) throws IOException {
        boolean inProgress = svc != null && svc.isStartupUpgradeInProgress();
        BatchInfo bi = svc != null ? svc.getIndexRebuildStatus() : null;
        int percent = bi != null ? bi.getPercent() : (inProgress ? 0 : 100);
        int current = bi != null ? bi.getCurrentPosition() : 0;
        int total = bi != null ? bi.getTotalSize() : 0;

        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/json; charset=UTF-8");
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        PrintWriter out = res.getWriter();
        out.write("{\"inProgress\":" + inProgress
                + ",\"percent\":" + percent
                + ",\"current\":" + current
                + ",\"total\":" + total + "}");
        out.flush();
    }

    private void writeUpgradeNoticePage(HttpServletResponse res, String contextPath, Jtrac svc) throws IOException {
        BatchInfo bi = svc.getIndexRebuildStatus();
        int percent = bi != null ? bi.getPercent() : 0;
        int current = bi != null ? bi.getCurrentPosition() : 0;
        int total = bi != null ? bi.getTotalSize() : 0;
        String base = (contextPath == null || "/".equals(contextPath)) ? "" : contextPath;
        String logoUrl = base + "/resources/jtrac-logo.svg";
        String statusUrl = base + STATUS_PATH;
        String loginUrl = base + "/app/login";

        res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        res.setHeader("Retry-After", "3");
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        res.setContentType("text/html; charset=UTF-8");

        StringBuilder html = new StringBuilder(2048);
        html.append("<!DOCTYPE html>\n<html lang=\"zh-Hant\">\n<head>\n")
            .append("  <meta charset=\"UTF-8\"/>\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n")
            .append("  <title>JTrac NG - System Upgrading</title>\n")
            .append("  <noscript><meta http-equiv=\"refresh\" content=\"3\"/></noscript>\n")
            .append("  <style>\n")
            .append("    :root { --bg:#F3F6FA; --card:#FFFFFF; --text:#1E293B; --muted:#64748B; --border:#E2E8F0; --accent:#2563EB; --track:#E2E8F0; }\n")
            .append("    @media (prefers-color-scheme: dark) { :root { --bg:#0F172A; --card:#1E293B; --text:#F1F5F9; --muted:#94A3B8; --border:#334155; --accent:#3B82F6; --track:#334155; } }\n")
            .append("    body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center; background:var(--bg); color:var(--text); font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans TC',sans-serif; padding:16px; box-sizing:border-box; }\n")
            .append("    .card { width:100%; max-width:520px; background:var(--card); border:1px solid var(--border); border-radius:12px; padding:32px 28px; box-shadow:0 10px 30px rgba(15,23,42,0.08); text-align:center; }\n")
            .append("    .logo { height:38px; margin-bottom:16px; }\n")
            .append("    .spinner { width:36px; height:36px; margin:0 auto 16px; border:3.5px solid var(--track); border-top-color:var(--accent); border-radius:50%; animation:spin 0.9s linear infinite; }\n")
            .append("    @keyframes spin { to { transform:rotate(360deg); } }\n")
            .append("    h1 { font-size:1.25rem; margin:0 0 10px; }\n")
            .append("    p { margin:0 0 10px; font-size:0.95rem; line-height:1.6; color:var(--muted); }\n")
            .append("    .bar-wrap { width:100%; height:10px; background:var(--track); border-radius:999px; overflow:hidden; margin:18px 0 10px; }\n")
            .append("    .bar-fill { height:100%; background:linear-gradient(90deg,#2563EB,#06B6D4); border-radius:999px; transition:width 0.4s ease; width:").append(percent).append("%; }\n")
            .append("    .progress-text { font-family:monospace; font-size:0.92rem; color:var(--text); font-weight:600; }\n")
            .append("    .hint { margin-top:14px; font-size:0.82rem; color:var(--muted); }\n")
            .append("  </style>\n</head>\n<body>\n")
            .append("  <div class=\"card\">\n")
            .append("    <img class=\"logo\" src=\"").append(logoUrl).append("\" alt=\"JTrac NG\" onerror=\"this.style.display='none'\"/>\n")
            .append("    <div class=\"spinner\"></div>\n")
            .append("    <h1>系統正在自動升級中 / System Upgrading</h1>\n")
            .append("    <p>偵測到舊版索引格式，正在升級全文檢索引擎（Apache Lucene 10）並重建歷史索引。完成後將自動進入登入頁面，請稍候且勿關閉伺服器。</p>\n")
            .append("    <p style=\"font-size:0.86rem;\">Legacy index detected. Upgrading search engine to Apache Lucene 10 and rebuilding indexes. You will be redirected to the login page automatically once finished.</p>\n")
            .append("    <div class=\"bar-wrap\"><div class=\"bar-fill\" id=\"bar\"></div></div>\n")
            .append("    <div class=\"progress-text\" id=\"ptxt\">").append(percent).append("% [").append(current).append(" / ").append(total).append("]</div>\n")
            .append("    <div class=\"hint\" id=\"hint\">自動偵測進度中... / Checking progress automatically...</div>\n")
            .append("  </div>\n")
            .append("  <script>\n")
            .append("    (function(){\n")
            .append("      var statusUrl = '").append(statusUrl).append("';\n")
            .append("      var loginUrl = '").append(loginUrl).append("';\n")
            .append("      function poll(){\n")
            .append("        fetch(statusUrl, { cache: 'no-store' })\n")
            .append("          .then(function(r){ return r.json(); })\n")
            .append("          .then(function(d){\n")
            .append("            var bar = document.getElementById('bar');\n")
            .append("            var ptxt = document.getElementById('ptxt');\n")
            .append("            if (bar) bar.style.width = (d.percent || 0) + '%';\n")
            .append("            if (ptxt) ptxt.textContent = (d.percent || 0) + '% [' + (d.current || 0) + ' / ' + (d.total || 0) + ']';\n")
            .append("            if (!d.inProgress) {\n")
            .append("              var hint = document.getElementById('hint');\n")
            .append("              if (hint) hint.textContent = '升級完成，正在前往登入頁面... / Upgrade complete, redirecting to login...';\n")
            .append("              window.location.replace(loginUrl);\n")
            .append("            } else {\n")
            .append("              setTimeout(poll, 1500);\n")
            .append("            }\n")
            .append("          })\n")
            .append("          .catch(function(){ setTimeout(poll, 2500); });\n")
            .append("      }\n")
            .append("      setTimeout(poll, 1500);\n")
            .append("    })();\n")
            .append("  </script>\n</body>\n</html>\n");

        PrintWriter out = res.getWriter();
        out.write(html.toString());
        out.flush();
    }
}
