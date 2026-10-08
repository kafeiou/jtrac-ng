package info.jtrac.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AttachmentTextExtractorTest {

    @Test
    public void testWhitelistAndBlacklistFilter() {
        // Whitelist
        Assert.assertTrue(AttachmentTextExtractor.isSupported("report.xlsx"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("spec.docx"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("manual.pdf"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("data.csv"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("notes.txt"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("README.md"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("server.log"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("package.zip"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("package.rar"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("image.png"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("photo.jpg"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("scan.jpeg"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("diagram.bmp"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("graphic.webp"));
        Assert.assertTrue(AttachmentTextExtractor.isSupported("animation.gif"));

        // Blacklist: legacy Office and binaries
        Assert.assertFalse(AttachmentTextExtractor.isSupported("legacy.doc"));
        Assert.assertFalse(AttachmentTextExtractor.isSupported("legacy.xls"));
        Assert.assertFalse(AttachmentTextExtractor.isSupported("presentation.ppt"));
        Assert.assertFalse(AttachmentTextExtractor.isSupported("package.7z"));
        Assert.assertFalse(AttachmentTextExtractor.isSupported("package.tar"));
        Assert.assertFalse(AttachmentTextExtractor.isSupported("installer.exe"));
        Assert.assertFalse(AttachmentTextExtractor.isSupported(null));
        Assert.assertFalse(AttachmentTextExtractor.isSupported("no_extension"));
    }

    @Test
    public void testExtractPlainTextAndCsv() throws Exception {
        File txtFile = File.createTempFile("jtrac-test-", ".txt");
        txtFile.deleteOnExit();
        Files.write(txtFile.toPath(), "JTrac 全文檢索純文字抽取測試\n第二行內容".getBytes(StandardCharsets.UTF_8));

        String text = AttachmentTextExtractor.extractText(txtFile);
        Assert.assertTrue(text.contains("JTrac 全文檢索純文字抽取測試"));
        Assert.assertTrue(text.contains("第二行內容"));
    }

    @Test
    public void testExtractPdfText() throws Exception {
        File pdfFile = File.createTempFile("jtrac-test-", ".pdf");
        pdfFile.deleteOnExit();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("JTrac PDF FullText Indexing Verification");
                cs.endText();
            }
            doc.save(pdfFile);
        }

        String extracted = AttachmentTextExtractor.extractText(pdfFile);
        Assert.assertTrue(extracted.contains("JTrac PDF FullText Indexing Verification"));
    }

    @Test
    public void testExtractDocxText() throws Exception {
        File docxFile = File.createTempFile("jtrac-test-", ".docx");
        docxFile.deleteOnExit();

        String documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
                "  <w:body>\n" +
                "    <w:p><w:r><w:t>JTrac OpenXML Word Document Text Extraction</w:t></w:r></w:p>\n" +
                "  </w:body>\n" +
                "</w:document>";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(docxFile))) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        String extracted = AttachmentTextExtractor.extractText(docxFile);
        Assert.assertTrue(extracted.contains("JTrac OpenXML Word Document Text Extraction"));
    }

    @Test
    public void testExtractXlsxText() throws Exception {
        File xlsxFile = File.createTempFile("jtrac-test-", ".xlsx");
        xlsxFile.deleteOnExit();

        String sharedStringsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"2\" uniqueCount=\"2\">\n" +
                "  <si><t>Alpha Project Specification</t></si>\n" +
                "  <si><t>Beta Testing Metric 2026</t></si>\n" +
                "</sst>";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(xlsxFile))) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(sharedStringsXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        String extracted = AttachmentTextExtractor.extractText(xlsxFile);
        Assert.assertTrue(extracted.contains("Alpha Project Specification"));
        Assert.assertTrue(extracted.contains("Beta Testing Metric 2026"));
    }

    @Test
    public void testExtractZipWithDocuments() throws Exception {
        File zipFile = File.createTempFile("jtrac-test-", ".zip");
        zipFile.deleteOnExit();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Entry 1: plain text
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("ZIP 壓縮檔案抽取測試 README".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Entry 2: subfolder CSV
            zos.putNextEntry(new ZipEntry("docs/data.csv"));
            zos.write("invoice_no,amount\nINV-888,9900".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        String extracted = AttachmentTextExtractor.extractText(zipFile);
        Assert.assertTrue(extracted.contains("[Archive: readme.txt]"));
        Assert.assertTrue(extracted.contains("ZIP 壓縮檔案抽取測試 README"));
        Assert.assertTrue(extracted.contains("[Archive: docs/data.csv]"));
        Assert.assertTrue(extracted.contains("INV-888,9900"));
    }

    @Test
    public void testExtractZipPathTraversalSecurity() throws Exception {
        File zipFile = File.createTempFile("jtrac-slip-", ".zip");
        zipFile.deleteOnExit();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("../traversal.txt"));
            zos.write("malicious content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("safe.txt"));
            zos.write("safe content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        String extracted = AttachmentTextExtractor.extractText(zipFile);
        Assert.assertFalse(extracted.contains("malicious content"));
        Assert.assertTrue(extracted.contains("[Archive: safe.txt]"));
        Assert.assertTrue(extracted.contains("safe content"));
    }

    @Test
    public void testImageOcrGracefulFallbackWithoutOllama() throws Exception {
        File imgFile = File.createTempFile("jtrac-ocr-", ".png");
        imgFile.deleteOnExit();
        Files.write(imgFile.toPath(), new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 });

        // Without Ollama configured, should return empty string gracefully
        String result = AttachmentTextExtractor.extractText(imgFile);
        Assert.assertEquals("", result);
    }

    @Test
    public void testExtractImageAndZipWithMockOllama() throws Exception {
        info.jtrac.mail.OllamaClient mockOllama = new info.jtrac.mail.OllamaClient("http://localhost:11434", "llama3.2-vision", null, 5) {
            @Override
            public String chatWithImage(String systemPrompt, String userPrompt, byte[] imageBytes, String customModel) {
                return "INVOICE #9988 TOTAL $3200";
            }
        };

        // 1. Standalone image OCR
        File pngFile = File.createTempFile("jtrac-ocr-", ".png");
        pngFile.deleteOnExit();
        Files.write(pngFile.toPath(), new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 });

        String imgText = AttachmentTextExtractor.extractText(pngFile, 10, 50000, mockOllama, "llama3.2-vision");
        Assert.assertTrue(imgText.contains("INVOICE #9988 TOTAL $3200"));

        // 2. Unpacked image inside ZIP archive
        File zipFile = File.createTempFile("jtrac-zip-ocr-", ".zip");
        zipFile.deleteOnExit();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("scans/receipt.jpg"));
            zos.write(new byte[] { (byte) 0xFF, (byte) 0xD8 });
            zos.closeEntry();
        }

        String zipText = AttachmentTextExtractor.extractText(zipFile, 10, 50000, mockOllama, null);
        Assert.assertTrue(zipText.contains("[Archive: scans/receipt.jpg]"));
        Assert.assertTrue(zipText.contains("INVOICE #9988 TOTAL $3200"));
    }

    @Test
    public void testCorruptedRarResilience() throws Exception {
        File corruptRar = File.createTempFile("jtrac-corrupt-", ".rar");
        corruptRar.deleteOnExit();
        Files.write(corruptRar.toPath(), "this is not a valid rar file".getBytes(StandardCharsets.UTF_8));

        String result = AttachmentTextExtractor.extractText(corruptRar);
        Assert.assertEquals("", result);
    }

    @Test
    public void testFileSizeGuardrail() throws Exception {
        File largeFile = File.createTempFile("jtrac-large-", ".txt");
        largeFile.deleteOnExit();

        byte[] chunk = new byte[1024 * 1024]; // 1MB
        Files.write(largeFile.toPath(), chunk);

        // When limit is 0MB (less than 1MB), should skip and return empty string
        String result = AttachmentTextExtractor.extractText(largeFile, 0, 100);
        Assert.assertEquals("", result);
    }

    @Test
    public void testBlacklistIgnoredGracefully() throws Exception {
        File docFile = File.createTempFile("jtrac-test-", ".doc");
        docFile.deleteOnExit();
        Files.write(docFile.toPath(), "legacy word content".getBytes(StandardCharsets.UTF_8));

        String result = AttachmentTextExtractor.extractText(docFile);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCorruptedFileResilience() throws Exception {
        File corruptZip = File.createTempFile("jtrac-corrupt-", ".docx");
        corruptZip.deleteOnExit();
        Files.write(corruptZip.toPath(), "this is not a zip file".getBytes(StandardCharsets.UTF_8));

        // Must not throw any exception, should return empty string
        String result = AttachmentTextExtractor.extractText(corruptZip);
        Assert.assertEquals("", result);
    }

    @Test
    public void testPreprocessImageDownscaleAndGif() throws Exception {
        // 1. High-resolution image downscaling (e.g. 2000 x 1500)
        java.awt.image.BufferedImage bigImg = new java.awt.image.BufferedImage(2000, 1500, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bigImg, "jpg", baos);
        byte[] bigBytes = baos.toByteArray();

        byte[] processedBig = AttachmentTextExtractor.preprocessImageForOcr(bigBytes, "large_screenshot.jpg");
        java.awt.image.BufferedImage scaled = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(processedBig));
        Assert.assertNotNull(scaled);
        Assert.assertTrue("Width should be <= 1280", scaled.getWidth() <= 1280);
        Assert.assertTrue("Height should be <= 1280", scaled.getHeight() <= 1280);

        // 2. GIF image preprocessing and normalization
        java.awt.image.BufferedImage gifImg = new java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream gifBaos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(gifImg, "gif", gifBaos);
        byte[] gifBytes = gifBaos.toByteArray();

        byte[] processedGif = AttachmentTextExtractor.preprocessImageForOcr(gifBytes, "bug_reproduce.gif");
        java.awt.image.BufferedImage readBack = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(processedGif));
        Assert.assertNotNull(readBack);
        Assert.assertEquals(200, readBack.getWidth());
        Assert.assertEquals(100, readBack.getHeight());
    }
}
