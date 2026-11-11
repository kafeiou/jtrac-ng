package info.jtrac.web;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

public class StaticResourceFilterTest {

    private StaticResourceFilter filter;

    @Before
    public void setUp() {
        filter = new StaticResourceFilter();
    }

    @Test
    public void testForwardNestedResource() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/app/resources/delete.gif");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertEquals("/resources/delete.gif", response.getForwardedUrl());
    }

    @Test
    public void testForwardDeeplyNestedResource() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/app/space/allocate/resources/edit.gif");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertEquals("/resources/edit.gif", response.getForwardedUrl());
    }

    @Test
    public void testPassThroughNormalResource() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/resources/delete.gif");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertNull(response.getForwardedUrl());
    }

    @Test
    public void testPassThroughAppPage() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/app/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assert.assertNull(response.getForwardedUrl());
    }
}
