/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring controller, for backwards compatibility with old email links
 */
public class DefaultMultiActionController extends AbstractMultiActionController {     

    private Properties mappings;

    public void setMappings(Properties mappings) {
        this.mappings = mappings;
    }

    @Override
    protected String getHandlerMethodName(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (mappings != null) {
            for (String key : mappings.stringPropertyNames()) {
                if (uri != null && (uri.endsWith(key) || uri.contains(key))) {
                    return mappings.getProperty(key);
                }
            }
        }
        return "itemViewHandler";
    }

    public ModelAndView itemViewHandler(HttpServletRequest request, HttpServletResponse response) {
        String itemId = request.getParameter("itemId");
        return new ModelAndView("redirect:/app/item/" + itemId);
    }

    public ModelAndView reportHandler(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String token = request.getParameter("token");
        if (token == null || !token.matches("^[a-zA-Z0-9\\-]+$")) {
            renderReportNotice(response, "無效的報告連結 (Invalid Report Token)",
                    "抱歉，您提供的報告憑證識別碼格式不正確或無效。<br>(The provided report token is missing or invalid.)");
            return null;
        }

        String home = (jtrac != null && jtrac.getJtracHome() != null) ? jtrac.getJtracHome() : (System.getProperty("user.home") + "/.jtrac");
        File reportsDir = new File(home, "reports");
        File reportFile = new File(reportsDir, token + ".html");

        // Canonical path check to prevent directory traversal
        if (!reportFile.getCanonicalPath().startsWith(reportsDir.getCanonicalPath())) {
            renderReportNotice(response, "非法請求 (Access Denied)", "權限不足或非法存取路徑。<br>(Invalid file path access.)");
            return null;
        }

        if (!reportFile.exists() || !reportFile.isFile()) {
            renderReportNotice(response, "報告已過期或不存在 (Report Not Found or Expired)",
                    "此 AI 診斷報告已超過 <strong>14 天</strong> 之系統保留期限，或該報告已被自動清理。<br>若需獲取最新分析，請再次發送郵件查詢或於 JTrac 內發起診斷。<br>(This report has expired under the 14-day retention limit or was removed.)");
            return null;
        }

        long fourteenDays = 14L * 24 * 60 * 60 * 1000;
        if (System.currentTimeMillis() - reportFile.lastModified() > fourteenDays) {
            reportFile.delete();
            renderReportNotice(response, "報告已過期 (Report Expired)",
                    "此 AI 診斷報告已超過 <strong>14 天</strong> 之系統保留期限，系統已進行安全銷毀。<br>若需最新分析，請重新發送郵件發起查詢。<br>(This report has expired under the 14-day retention limit.)");
            return null;
        }

        String download = request.getParameter("download");
        if ("true".equalsIgnoreCase(download) || "1".equals(download)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmm");
            String dlName = "JTrac-AI-Report-" + sdf.format(new Date(reportFile.lastModified())) + ".html";
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + dlName + "\"");
            response.setContentLengthLong(reportFile.length());
            try (InputStream in = new FileInputStream(reportFile);
                 OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.flush();
            }
            return null;
        }

        // Direct Browser View
        response.setContentType("text/html; charset=UTF-8");
        response.setContentLengthLong(reportFile.length());
        try (InputStream in = new FileInputStream(reportFile);
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
        return null;
    }

    private void renderReportNotice(HttpServletResponse response, String title, String message) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        out.println("<title>" + title + "</title>");
        out.println("<style>");
        out.println("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f6f8fa; color: #24292e; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; }");
        out.println(".card { background: #fff; border: 1px solid #d0d7de; border-radius: 10px; max-width: 540px; padding: 36px 32px; text-align: center; box-shadow: 0 4px 12px rgba(0,0,0,0.06); }");
        out.println("h2 { color: #cf222e; margin: 0 0 16px 0; font-size: 20px; }");
        out.println("p { line-height: 1.6; color: #57606a; font-size: 14px; margin: 0 0 24px 0; }");
        out.println(".btn { background: #0969da; color: #fff; padding: 10px 20px; border-radius: 6px; text-decoration: none; font-size: 14px; font-weight: 600; display: inline-block; }");
        out.println("</style></head><body>");
        out.println("<div class='card'>");
        out.println("<h2>⏰ " + title + "</h2>");
        out.println("<p>" + message + "</p>");
        out.println("<a href='javascript:window.close()' class='btn'>關閉視窗 (Close)</a>");
        out.println("</div></body></html>");
        out.flush();
    }
}
