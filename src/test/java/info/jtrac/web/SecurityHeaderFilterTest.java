package info.jtrac.web;

import info.jtrac.Jtrac;
import java.io.IOException;
import java.lang.reflect.Proxy;
import jakarta.servlet.ServletException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class SecurityHeaderFilterTest {

    private SecurityHeaderFilter filter;

    @Before
    public void setUp() {
        filter = new SecurityHeaderFilter();
    }

    private Jtrac createMockJtrac(final String configValue) {
        return (Jtrac) Proxy.newProxyInstance(
                Jtrac.class.getClassLoader(),
                new Class<?>[]{Jtrac.class},
                (proxy, method, args) -> {
                    if ("loadConfig".equals(method.getName()) && args != null && args.length > 0) {
                        if (SecurityHeaderFilter.CONFIG_KEY.equals(args[0])) {
                            return configValue;
                        }
                    }
                    return null;
                }
        );
    }

    @Test
    public void testDefaultHeadersInjectedWhenNoConfigPresent() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertEquals("noindex, nofollow, noarchive, nosnippet", response.getHeader("X-Robots-Tag"));
        Assert.assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        Assert.assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        Assert.assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void testHeadersInjectedWhenExplicitlyEnabled() throws IOException, ServletException {
        Jtrac mockJtrac = createMockJtrac("true");
        filter.setJtrac(mockJtrac);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertEquals("noindex, nofollow, noarchive, nosnippet", response.getHeader("X-Robots-Tag"));
        Assert.assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        Assert.assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        Assert.assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void testHeadersOmittedWhenExplicitlyDisabled() throws IOException, ServletException {
        Jtrac mockJtrac = createMockJtrac("false");
        filter.setJtrac(mockJtrac);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertNull(response.getHeader("X-Robots-Tag"));
        Assert.assertNull(response.getHeader("Referrer-Policy"));
        Assert.assertNull(response.getHeader("X-Frame-Options"));
        Assert.assertNull(response.getHeader("X-Content-Type-Options"));
    }
}
