package info.jtrac.wicket;

import info.jtrac.domain.User;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.ListModel;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.http.WebResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * SuperUser-only administration page for full system backup download and ZIP restore.
 */
public class BackupRestorePage extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(BackupRestorePage.class);

    public BackupRestorePage() {
        final User user = getPrincipal();
        if (user == null || !user.isSuperUser()) {
            throw new RestartResponseAtInterceptPageException(DashboardPage.class);
        }

        addOptionsBreadcrumb();
        add(new FeedbackPanel("feedback"));

        // 1. Export Form
        Form<Void> exportForm = new Form<Void>("exportForm");
        add(exportForm);

        exportForm.add(new Button("exportButton") {
            @Override
            public void onSubmit() {
                final String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                final String zipFileName = "jtrac-backup-" + timestamp + ".zip";

                getRequestCycle().scheduleRequestHandlerAfterCurrent(new IRequestHandler() {
                    public void detach(IRequestCycle requestCycle) {}

                    public void respond(IRequestCycle requestCycle) {
                        WebResponse r = (WebResponse) requestCycle.getResponse();
                        r.setContentType("application/zip");
                        r.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
                        try {
                            getJtrac().exportBackupZip(r.getOutputStream(), user.getLoginName());
                        } catch (Exception e) {
                            logger.error("Failed to export backup ZIP", e);
                            throw new RuntimeException("Export backup failed: " + e.getMessage(), e);
                        }
                    }
                });
            }
        });

        // 2. Restore Form
        final FileUploadField fileUploadField = new FileUploadField("file", new ListModel<FileUpload>());
        final CheckBox confirmCheck = new CheckBox("confirmCheck", new Model<Boolean>(Boolean.FALSE));

        Form<Void> restoreForm = new Form<Void>("restoreForm") {
            @Override
            public void onSubmit() {
                FileUpload fileUpload = fileUploadField.getFileUpload();
                if (fileUpload == null || fileUpload.getClientFileName() == null || fileUpload.getClientFileName().trim().isEmpty()) {
                    error(localize("backup_restore.error.noFileSelected"));
                    return;
                }

                String fileName = fileUpload.getClientFileName().toLowerCase();
                if (!fileName.endsWith(".zip")) {
                    error(localize("backup_restore.error.invalidZipFile"));
                    return;
                }

                Boolean confirmed = confirmCheck.getModelObject();
                if (confirmed == null || !confirmed) {
                    error(localize("backup_restore.error.confirmationRequired"));
                    return;
                }

                try (InputStream is = fileUpload.getInputStream()) {
                    getJtrac().performFullRestore(is, user);
                    info(localize("backup_restore.restore_success"));
                } catch (Exception e) {
                    logger.error("Restore failed", e);
                    error(localize("backup_restore.restore_failed") + ": " + e.getMessage());
                }
            }
        };
        add(restoreForm);

        restoreForm.setMultiPart(true);
        restoreForm.add(fileUploadField);
        restoreForm.add(confirmCheck);
        restoreForm.add(new Button("restoreButton"));

        // 3. Cancel link back to OptionsPage
        restoreForm.add(new Link<Void>("cancel") {
            @Override
            public void onClick() {
                setResponsePage(OptionsPage.class);
            }
        });
    }
}
