package info.jtrac.web;

import info.jtrac.Jtrac;
import info.jtrac.domain.BatchInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies StartupUpgradeFilter intercepts web requests with an HTTP 503 progress page
 * during startup Lucene upgrade, serves live JSON status, allows static assets, and
 * passes traffic through immediately once the upgrade completes.
 */
public class StartupUpgradeFilterTest {

    private Jtrac createStubJtrac(final AtomicBoolean upgrading, final BatchInfo batchInfo) {
        return (Jtrac) Proxy.newProxyInstance(
                Jtrac.class.getClassLoader(),
                new Class<?>[]{Jtrac.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("isStartupUpgradeInProgress".equals(method.getName())) {
                            return upgrading.get();
                        }
                        if ("getIndexRebuildStatus".equals(method.getName())) {
                            return batchInfo;
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        return null;
                    }
                });
    }

    private HttpServletRequest mockRequest(final String contextPath, final String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getContextPath".equals(method.getName())) return contextPath;
                        if ("getRequestURI".equals(method.getName())) return uri;
                        return null;
                    }
                });
    }

    private HttpServletResponse mockResponse(final AtomicInteger status, final StringWriter body) {
        final PrintWriter pw = new PrintWriter(body);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("setStatus".equals(method.getName()) && args != null && args.length == 1) {
                            status.set((Integer) args[0]);
                        }
                        if ("getWriter".equals(method.getName())) {
                            return pw;
                        }
                        return null;
                    }
                });
    }

    @Test
    public void testUpgradeInProgressShows503NoticeAndStatusJsonThenReleases() throws Exception {
        AtomicBoolean upgrading = new AtomicBoolean(true);
        BatchInfo bi = new BatchInfo();
        bi.setTotalSize(200);
        bi.setCurrentPosition(50);

        StartupUpgradeFilter filter = new StartupUpgradeFilter();
        filter.setJtrac(createStubJtrac(upgrading, bi));

        // 1. Page request during upgrade -> HTTP 503 with progress HTML, chain NOT called
        AtomicInteger status = new AtomicInteger(0);
        StringWriter htmlBody = new StringWriter();
        final AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(mockRequest("", "/app/login"), mockResponse(status, htmlBody), chain);
        Assert.assertEquals(503, status.get());
        Assert.assertFalse("Downstream chain must be blocked while upgrading", chainCalled.get());
        Assert.assertTrue(htmlBody.toString().contains("25% [50 / 200]"));
        Assert.assertTrue(htmlBody.toString().contains("/startup-upgrade-status"));
        Assert.assertTrue(htmlBody.toString().contains("/app/login"));

        // 2. JSON status endpoint -> HTTP 200 with live progress
        status.set(0);
        StringWriter jsonBody = new StringWriter();
        filter.doFilter(mockRequest("", "/startup-upgrade-status"), mockResponse(status, jsonBody), chain);
        Assert.assertEquals(200, status.get());
        Assert.assertEquals("{\"inProgress\":true,\"percent\":25,\"current\":50,\"total\":200}", jsonBody.toString());

        // 3. Static asset under /resources/ -> allowed through during upgrade
        chainCalled.set(false);
        filter.doFilter(mockRequest("", "/resources/jtrac-logo.svg"), mockResponse(status, new StringWriter()), chain);
        Assert.assertTrue("Static logo asset must pass through during upgrade", chainCalled.get());

        // 4. Upgrade completes -> normal requests pass straight through & JSON reports inProgress=false
        upgrading.set(false);
        bi.setCurrentPosition(200);
        bi.setComplete(true);

        chainCalled.set(false);
        filter.doFilter(mockRequest("", "/app/login"), mockResponse(status, new StringWriter()), chain);
        Assert.assertTrue("Normal login request must pass through once upgrade finishes", chainCalled.get());

        status.set(0);
        StringWriter doneJson = new StringWriter();
        filter.doFilter(mockRequest("", "/startup-upgrade-status"), mockResponse(status, doneJson), chain);
        Assert.assertEquals(200, status.get());
        Assert.assertEquals("{\"inProgress\":false,\"percent\":100,\"current\":200,\"total\":200}", doneJson.toString());
    }
}
