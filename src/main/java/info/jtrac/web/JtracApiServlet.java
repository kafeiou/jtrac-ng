package info.jtrac.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;

/**
 * Lightweight Jakarta HttpServlet that replaces Spring MVC DispatcherServlet for
 * handling REST API requests (/api/*) and legacy/flow requests (/flow/*).
 */
public class JtracApiServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(JtracApiServlet.class);

    private RestMultiActionController restController;
    private DefaultMultiActionController defaultController;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        WebApplicationContext wac = WebApplicationContextUtils.getWebApplicationContext(config.getServletContext());
        if (wac != null) {
            if (wac.containsBean("restController")) {
                this.restController = (RestMultiActionController) wac.getBean("restController");
            }
            if (wac.containsBean("defaultController")) {
                this.defaultController = (DefaultMultiActionController) wac.getBean("defaultController");
            }
        }
        if (this.restController == null) {
            logger.warn("restController bean not found in Spring context, creating fallback instance");
            this.restController = new RestMultiActionController();
        }
        if (this.defaultController == null) {
            logger.warn("defaultController bean not found in Spring context, creating fallback instance");
            this.defaultController = new DefaultMultiActionController();
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = (contextPath != null && uri.startsWith(contextPath)) ? uri.substring(contextPath.length()) : uri;

        try {
            if (path.startsWith("/api")) {
                restController.handleRequest(request, response);
            } else {
                defaultController.handleRequest(request, response);
            }
        } catch (ServletException | IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error processing request for " + uri + ": " + e.getMessage(), e);
            throw new ServletException(e);
        }
    }
}
