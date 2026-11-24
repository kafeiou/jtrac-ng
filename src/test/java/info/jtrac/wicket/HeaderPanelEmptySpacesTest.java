package info.jtrac.wicket;

import info.jtrac.Jtrac;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import org.apache.wicket.resource.loader.BundleStringResourceLoader;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * Verifies HeaderPanel markup rendering when zero project spaces exist vs when spaces exist,
 * preventing empty button shells (nav-tab-quicksearch / nav-search-divider) from rendering.
 */
public class HeaderPanelEmptySpacesTest {

    private WicketTester tester;

    @Before
    public void setUp() {
        tester = new WicketTester();
        tester.getApplication().getResourceSettings().getStringResourceLoaders()
                .add(0, new BundleStringResourceLoader("messages"));
    }

    @After
    public void tearDown() {
        if (tester != null) {
            tester.destroy();
        }
    }

    private Jtrac createStubJtrac(final List<Space> allSpaces) {
        return (Jtrac) Proxy.newProxyInstance(
                Jtrac.class.getClassLoader(),
                new Class<?>[]{Jtrac.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("findAllSpaces".equals(name)) {
                            return allSpaces;
                        }
                        if ("getReleaseVersion".equals(name)) {
                            return "3.0.0-beta";
                        }
                        if ("getReleaseTimestamp".equals(name)) {
                            return "20261124";
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        return null;
                    }
                });
    }

    @Test
    public void testSearchContainersCompletelyHiddenWhenNoSpacesExist() {
        final User admin = new User();
        admin.setId(1L);
        admin.setName("Admin");
        admin.setLoginName("admin");
        admin.addSpaceWithRole(null, "ROLE_ADMIN");

        final Jtrac stubJtrac = createStubJtrac(Collections.<Space>emptyList());

        HeaderPanel panel = new HeaderPanel() {
            @Override
            protected User getPrincipal() {
                return admin;
            }

            @Override
            protected Space getCurrentSpace() {
                return null;
            }

            @Override
            protected Jtrac getJtrac() {
                return stubJtrac;
            }
        };

        tester.startComponentInPage(panel);
        String html = tester.getLastResponseAsString();

        Assert.assertFalse("nav-tab-quicksearch td must NOT render when spaces is empty",
                html.contains("nav-tab-quicksearch"));
        Assert.assertFalse("nav-search-divider must NOT render when spaces is empty",
                html.contains("nav-search-divider"));
        Assert.assertFalse("nav-tab-search must NOT render when spaces is empty",
                html.contains("nav-tab-search"));
        Assert.assertFalse("mobile-search-btn must NOT render when spaces is empty",
                html.contains("mobile-search-btn"));
        Assert.assertFalse("mobile-search-drawer must NOT render when spaces is empty",
                html.contains("mobile-search-drawer"));
    }

    @Test
    public void testSearchContainersRenderedNormallyWhenSpaceExists() {
        final User admin = new User();
        admin.setId(1L);
        admin.setName("Admin");
        admin.setLoginName("admin");
        admin.addSpaceWithRole(null, "ROLE_ADMIN");

        Space space = new Space();
        space.setId(1L);
        space.setPrefixCode("DEMO");
        space.setName("Demo Project");

        final Jtrac stubJtrac = createStubJtrac(Collections.singletonList(space));

        HeaderPanel panel = new HeaderPanel() {
            @Override
            protected User getPrincipal() {
                return admin;
            }

            @Override
            protected Space getCurrentSpace() {
                return null;
            }

            @Override
            protected Jtrac getJtrac() {
                return stubJtrac;
            }
        };

        tester.startComponentInPage(panel);
        String html = tester.getLastResponseAsString();

        Assert.assertTrue("nav-tab-quicksearch td must render when space exists",
                html.contains("nav-tab-quicksearch"));
        Assert.assertTrue("desktop-search-form must render when space exists",
                html.contains("desktop-search-form"));
        Assert.assertTrue("nav-tab-search must render when space exists",
                html.contains("nav-tab-search"));
        Assert.assertTrue("mobile-search-btn must render when space exists",
                html.contains("mobile-search-btn"));
        Assert.assertTrue("mobile-search-drawer must render when space exists",
                html.contains("mobile-search-drawer"));
    }
}
