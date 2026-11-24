package info.jtrac.wicket;

import info.jtrac.domain.User;
import info.jtrac.sbom.SbomService;
import info.jtrac.sbom.SbomService.ComponentItem;
import info.jtrac.sbom.SbomService.EnvironmentInfo;
import info.jtrac.sbom.SbomService.SbomSnapshot;
import jakarta.servlet.ServletContext;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.http.WebResponse;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * SuperUser-only administration page for Runtime Software Bill of Materials (SBOM)
 * inventory inspection, persistent snapshot refresh, and CycloneDX 1.5 JSON / UTF-8 BOM CSV export.
 */
public class SbomPage extends BasePage {

    public SbomPage() {
        this(false);
    }

    public SbomPage(boolean forceRefresh) {
        final User user = getPrincipal();
        if (user == null || !user.isSuperUser()) {
            throw new RestartResponseAtInterceptPageException(DashboardPage.class);
        }

        ServletContext sc = null;
        try {
            if (getApplication() instanceof WebApplication) {
                sc = ((WebApplication) getApplication()).getServletContext();
            }
        } catch (Exception ignored) {
        }

        final SbomSnapshot snapshot = SbomService.getSnapshot(
                getJtrac(),
                getJtrac() != null ? getJtrac().getDataSource() : null,
                sc,
                forceRefresh);

        // Top navigation & action buttons
        addOptionsBreadcrumb();

        add(new Link<Void>("refreshBtn") {
            @Override
            public void onClick() {
                setResponsePage(new SbomPage(true));
            }
        });

        add(new Link<Void>("exportCycloneDxBtn") {
            @Override
            public void onClick() {
                final String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                final String fileName = "jtrac-ng-sbom-cyclonedx-" + ts + ".json";
                final byte[] payload = SbomService.exportCycloneDxJson(snapshot).getBytes(StandardCharsets.UTF_8);
                streamDownload(fileName, "application/vnd.cyclonedx+json; charset=UTF-8", payload);
            }
        });

        add(new Link<Void>("exportCsvBtn") {
            @Override
            public void onClick() {
                final String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                final String fileName = "jtrac-ng-sbom-" + ts + ".csv";
                final byte[] payload = SbomService.exportCsv(snapshot).getBytes(StandardCharsets.UTF_8);
                streamDownload(fileName, "text/csv; charset=UTF-8", payload);
            }
        });

        // Runtime Environment Summary
        EnvironmentInfo env = snapshot.environment != null ? snapshot.environment : new EnvironmentInfo();
        add(new Label("generatedAt", snapshot.generatedAtDisplay.isEmpty() ? snapshot.generatedAt : snapshot.generatedAtDisplay));
        add(new Label("envApp", "JTrac NG" + (env.appVersion.isEmpty() ? "" : " " + env.appVersion)));
        add(new Label("envServer", env.serverInfo.isEmpty() ? "-" : env.serverInfo));
        add(new Label("envJava", env.javaVersion.isEmpty() ? "-" : env.javaVersion));
        add(new Label("envJavaVendor", env.javaVendor.isEmpty() ? "" : "(" + env.javaVendor + " / " + env.jvmName + ")"));
        add(new Label("envOs", (env.osName + " " + env.osVersion).trim() + (env.osArch.isEmpty() ? "" : " (" + env.osArch + ")")));
        add(new Label("envDb", (env.dbProduct + " " + env.dbVersion).trim().isEmpty() ? "-" : (env.dbProduct + " " + env.dbVersion).trim()));
        add(new Label("envJdbc", (env.jdbcDriver + " " + env.jdbcDriverVersion).trim()));

        Label ollamaBadge = new Label("envOllamaStatus", localize(env.ollamaConfigured ? "sbom.status.enabled" : "sbom.status.disabled"));
        ollamaBadge.add(new AttributeAppender("class", env.ollamaConfigured ? " sbom-badge-ok" : " sbom-badge-muted"));
        add(ollamaBadge);
        add(new Label("envOllamaModel", env.ollamaModel.isEmpty() ? "-" : env.ollamaModel));

        // Section 1: Core Subsystems
        add(new Label("subsystemsCount", String.valueOf(snapshot.subsystems.size())));
        add(new ListView<ComponentItem>("subsystemsList", snapshot.subsystems) {
            @Override
            protected void populateItem(ListItem<ComponentItem> item) {
                ComponentItem c = item.getModelObject();
                item.add(new Label("group", c.group.isEmpty() ? "-" : c.group));
                item.add(new Label("name", c.name));
                item.add(new Label("version", c.version.isEmpty() ? "-" : c.version));
                boolean enabled = "ENABLED".equalsIgnoreCase(c.status);
                Label statusLbl = new Label("status", localize(enabled ? "sbom.status.enabled" : "sbom.status.disabled"));
                statusLbl.add(new AttributeAppender("class", enabled ? " sbom-badge-ok" : " sbom-badge-muted"));
                item.add(statusLbl);
            }
        });

        // Section 2: Frontend & Bundled Assets
        add(new Label("frontendCount", String.valueOf(snapshot.frontendLibraries.size())));
        add(new ListView<ComponentItem>("frontendList", snapshot.frontendLibraries) {
            @Override
            protected void populateItem(ListItem<ComponentItem> item) {
                ComponentItem c = item.getModelObject();
                item.add(new Label("group", c.group.isEmpty() ? "-" : c.group));
                item.add(new Label("name", c.name));
                item.add(new Label("version", c.version.isEmpty() ? "-" : c.version));
            }
        });

        // Section 3: Backend Java Libraries
        add(new Label("backendCount", String.valueOf(snapshot.backendLibraries.size())));
        add(new ListView<ComponentItem>("backendList", snapshot.backendLibraries) {
            @Override
            protected void populateItem(ListItem<ComponentItem> item) {
                ComponentItem c = item.getModelObject();
                item.add(new Label("group", c.group.isEmpty() ? "-" : c.group));
                item.add(new Label("name", c.name));
                item.add(new Label("version", c.version.isEmpty() ? "-" : c.version));
                WebMarkupContainer purlWrap = new WebMarkupContainer("purlWrap");
                purlWrap.setVisible(!c.purl.isEmpty());
                purlWrap.add(new Label("purl", c.purl));
                item.add(purlWrap);
            }
        });
    }

    private void streamDownload(final String fileName, final String contentType, final byte[] payload) {
        getRequestCycle().scheduleRequestHandlerAfterCurrent(new IRequestHandler() {
            @Override
            public void detach(IRequestCycle requestCycle) {
            }

            @Override
            public void respond(IRequestCycle requestCycle) {
                WebResponse r = (WebResponse) requestCycle.getResponse();
                r.setContentType(contentType);
                r.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                r.setContentLength(payload.length);
                r.write(payload);
            }
        });
    }
}
