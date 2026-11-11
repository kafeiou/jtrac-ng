package info.jtrac.web;

import info.jtrac.Jtrac;
import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Filter that applies HTTP security and privacy headers to prevent search engine
 * indexing, prevent referrer leakage, and guard against clickjacking and MIME-sniffing.
 */
public class SecurityHeaderFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityHeaderFilter.class);

    public static final String CONFIG_KEY = "security.privacy.headers.enabled";
    public static final String HEADER_ROBOTS = "X-Robots-Tag";
    public static final String HEADER_ROBOTS_VALUE = "noindex, nofollow, noarchive, nosnippet";
    public static final String HEADER_REFERRER = "Referrer-Policy";
    public static final String HEADER_REFERRER_VALUE = "no-referrer";
    public static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_FRAME_OPTIONS_VALUE = "SAMEORIGIN";
    public static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

    private ServletContext servletContext;
    private Jtrac jtrac;

    public void setJtrac(Jtrac jtrac) {
        this.jtrac = jtrac;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (isPrivacyHeadersEnabled()) {
                httpResponse.setHeader(HEADER_ROBOTS, HEADER_ROBOTS_VALUE);
                httpResponse.setHeader(HEADER_REFERRER, HEADER_REFERRER_VALUE);
                httpResponse.setHeader(HEADER_FRAME_OPTIONS, HEADER_FRAME_OPTIONS_VALUE);
                httpResponse.setHeader(HEADER_CONTENT_TYPE_OPTIONS, HEADER_CONTENT_TYPE_OPTIONS_VALUE);
            }
        }
        chain.doFilter(request, response);
    }

    protected boolean isPrivacyHeadersEnabled() {
        Jtrac jtracService = getJtrac();
        if (jtracService == null) {
            // Safe default if Spring context is not yet loaded
            return true;
        }
        try {
            String val = jtracService.loadConfig(CONFIG_KEY);
            if (val == null || val.trim().isEmpty()) {
                return true;
            }
            return "true".equalsIgnoreCase(val.trim());
        } catch (Exception e) {
            logger.warn("Failed to read config '{}', defaulting to true: {}", CONFIG_KEY, e.getMessage());
            return true;
        }
    }

    private synchronized Jtrac getJtrac() {
        if (this.jtrac != null) {
            return this.jtrac;
        }
        if (this.servletContext != null) {
            try {
                WebApplicationContext wac = WebApplicationContextUtils.getWebApplicationContext(this.servletContext);
                if (wac != null && wac.containsBean("jtrac")) {
                    this.jtrac = (Jtrac) wac.getBean("jtrac");
                }
            } catch (Exception e) {
                logger.debug("WebApplicationContext not yet available: {}", e.getMessage());
            }
        }
        return this.jtrac;
    }

    @Override
    public void destroy() {
        this.jtrac = null;
        this.servletContext = null;
    }
}
