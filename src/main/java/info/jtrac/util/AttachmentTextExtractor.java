package info.jtrac.util;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import info.jtrac.mail.OllamaClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Text extractor for attachments with robust guardrails:
 * 1. Strict Whitelist (.xlsx, .docx, .pdf, .txt, .csv, .md, .log, .zip, .rar, .png, .jpg, .jpeg, .bmp, .webp, .gif)
 * 2. Explicit Blacklist (.doc, .xls, .ppt, .7z, .tar, .gz, .exe, binaries)
 * 3. File size limit (default: 10MB) & max character extraction limit (default: 50,000 chars)
 * 4. Zero-dependency streaming OpenXML (.xlsx/.docx) parsing in standard UTF-8
 * 5. Smart charset detection for plain text and CSV
 * 6. Apache PDFBox integration for PDF documents
 * 7. Archive extraction (.zip, .rar) with path traversal (Zip Slip) and zip bomb defense
 * 8. Ollama Multimodal Vision OCR with auto-downscaling guardrail to prevent GPU VRAM exhaustion
 */
public class AttachmentTextExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentTextExtractor.class);

    public static final int DEFAULT_MAX_SIZE_MB = 10;
    public static final int DEFAULT_MAX_CHARS = 50000;
    public static final int MAX_ARCHIVE_ENTRIES = 100;
    public static final long MAX_ENTRY_SIZE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 30L * 1024 * 1024;
    public static final int MAX_IMAGE_OCR_DIMENSION = 1280;

    private static final Set<String> WHITELIST = new HashSet<String>(Arrays.asList(
            "xlsx", "docx", "pdf", "txt", "csv", "md", "log",
            "zip", "rar",
            "png", "jpg", "jpeg", "bmp", "webp", "gif"
    ));

    private static final Set<String> BLACKLIST = new HashSet<String>(Arrays.asList(
            "doc", "xls", "ppt", "7z", "tar", "gz",
            "exe", "dll", "bin", "iso",
            "mp3", "mp4", "avi", "mov", "wav", "class", "jar"
    ));

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<String>(Arrays.asList(
            "png", "jpg", "jpeg", "bmp", "webp", "gif"
    ));

    private static final Set<String> INNER_SUPPORTED_EXTENSIONS = new HashSet<String>(Arrays.asList(
            "xlsx", "docx", "pdf", "txt", "csv", "md", "log",
            "png", "jpg", "jpeg", "bmp", "webp", "gif"
    ));

    /**
     * Checks if the given file extension is supported for full-text indexing.
     */
    public static boolean isSupported(String fileName) {
        if (fileName == null) {
            return false;
        }
        String ext = getExtension(fileName);
        if (ext.isEmpty() || BLACKLIST.contains(ext)) {
            return false;
        }
        return WHITELIST.contains(ext);
    }

    /**
     * Extract clean text content from an attachment file using default guardrail limits.
     */
    public static String extractText(File file) {
        return extractText(file, DEFAULT_MAX_SIZE_MB, DEFAULT_MAX_CHARS, null, null);
    }

    /**
     * Extract clean text content from an attachment file using specified limits.
     */
    public static String extractText(File file, int maxSizeMb, int maxChars) {
        return extractText(file, maxSizeMb, maxChars, null, null);
    }

    /**
     * Extract clean text content from an attachment file using specified limits and optional Ollama OCR client.
     *
     * @param file          The file to extract from
     * @param maxSizeMb     Maximum allowed file size in MB
     * @param maxChars      Maximum number of characters to extract
     * @param ollamaClient  Optional OllamaClient for Multimodal Vision OCR (null to skip OCR)
     * @param ocrModel      Optional dedicated OCR model name (e.g. "llama3.2-vision")
     * @return Extracted plain text, or empty string if unsupported or failed
     */
    public static String extractText(File file, int maxSizeMb, int maxChars, OllamaClient ollamaClient, String ocrModel) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }

        String fileName = file.getName();
        if (!isSupported(fileName)) {
            logger.debug("Skipping unsupported or blacklisted attachment: {}", fileName);
            return "";
        }

        long maxBytes = (long) (maxSizeMb > 0 ? maxSizeMb : DEFAULT_MAX_SIZE_MB) * 1024 * 1024;
        if (file.length() > maxBytes) {
            logger.info("Skipping attachment '{}': size ({} bytes) exceeds limit ({} MB)",
                    fileName, file.length(), maxSizeMb);
            return "";
        }

        int limitChars = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        String ext = getExtension(fileName);

        try {
            if ("pdf".equals(ext)) {
                return extractPdfText(file, limitChars);
            } else if ("docx".equals(ext)) {
                return extractDocxText(file, limitChars);
            } else if ("xlsx".equals(ext)) {
                return extractXlsxText(file, limitChars);
            } else if ("zip".equals(ext)) {
                return extractZipText(file, limitChars, ollamaClient, ocrModel);
            } else if ("rar".equals(ext)) {
                return extractRarText(file, limitChars, ollamaClient, ocrModel);
            } else if (IMAGE_EXTENSIONS.contains(ext)) {
                if (ollamaClient == null) {
                    logger.debug("Skipping OCR for image '{}': Ollama OCR is disabled or not configured.", fileName);
                    return "";
                }
                byte[] imageBytes = Files.readAllBytes(file.toPath());
                return extractImageOcr(imageBytes, fileName, limitChars, ollamaClient, ocrModel);
            } else {
                // txt, csv, md, log
                return extractPlainText(file, limitChars);
            }
        } catch (Throwable t) {
            logger.warn("Non-fatal error extracting text from attachment '{}': {}", fileName, t.getMessage());
            return "";
        }
    }

    private static String extractPdfText(File file, int maxChars) throws Exception {
        try (PDDocument document = PDDocument.load(file)) {
            if (document.isEncrypted()) {
                logger.info("PDF attachment '{}' is encrypted/password-protected, skipping text extraction.", file.getName());
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return truncate(text, maxChars);
        }
    }

    private static String extractDocxText(File file, int maxChars) throws Exception {
        try (InputStream fis = new FileInputStream(file)) {
            return extractDocxStream(fis, maxChars);
        }
    }

    private static String extractDocxStream(InputStream is, int maxChars) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            XMLInputFactory xmlFactory = XMLInputFactory.newFactory();
            // Prevent XXE attacks
            xmlFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xmlFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equalsIgnoreCase(entry.getName())) {
                    XMLStreamReader reader = xmlFactory.createXMLStreamReader(zis, "UTF-8");
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            String text = reader.getText();
                            if (text != null && !text.isEmpty()) {
                                sb.append(text).append(' ');
                                if (sb.length() >= maxChars) {
                                    break;
                                }
                            }
                        }
                    }
                    reader.close();
                    break;
                }
            }
        }
        return truncate(sb.toString(), maxChars);
    }

    private static String extractXlsxText(File file, int maxChars) throws Exception {
        try (InputStream fis = new FileInputStream(file)) {
            return extractXlsxStream(fis, maxChars);
        }
    }

    private static String extractXlsxStream(InputStream is, int maxChars) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            XMLInputFactory xmlFactory = XMLInputFactory.newFactory();
            xmlFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xmlFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/sharedStrings.xml".equalsIgnoreCase(entry.getName())) {
                    XMLStreamReader reader = xmlFactory.createXMLStreamReader(zis, "UTF-8");
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            String text = reader.getText();
                            if (text != null && !text.isEmpty()) {
                                sb.append(text).append(' ');
                                if (sb.length() >= maxChars) {
                                    break;
                                }
                            }
                        }
                    }
                    reader.close();
                    break;
                }
            }
        }
        return truncate(sb.toString(), maxChars);
    }

    private static String extractZipText(File file, int limitChars, OllamaClient ollamaClient, String ocrModel) {
        StringBuilder sb = new StringBuilder();
        try (InputStream fis = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytesRead = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    logger.warn("ZIP archive '{}' exceeded max entry count ({}), stopping extraction.", file.getName(), MAX_ARCHIVE_ENTRIES);
                    break;
                }
                String entryName = entry.getName();
                if (entryName == null || entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    logger.warn("Skipping ZIP entry with potential path traversal: {}", entryName);
                    continue;
                }
                String ext = getExtension(entryName);
                if (!INNER_SUPPORTED_EXTENSIONS.contains(ext)) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                long entryBytesRead = 0;
                while ((n = zis.read(buffer)) != -1) {
                    entryBytesRead += n;
                    totalBytesRead += n;
                    if (entryBytesRead > MAX_ENTRY_SIZE_BYTES || totalBytesRead > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                        logger.warn("ZIP entry '{}' exceeded uncompressed byte limits, skipping rest.", entryName);
                        break;
                    }
                    baos.write(buffer, 0, n);
                }

                byte[] entryBytes = baos.toByteArray();
                String text = extractInnerEntryText(entryName, ext, entryBytes, limitChars, ollamaClient, ocrModel);
                if (text != null && !text.trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append("[Archive: ").append(entryName).append("]\n").append(text.trim());
                    if (sb.length() >= limitChars) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn("Non-fatal error reading ZIP archive '{}': {}", file.getName(), t.getMessage());
        }
        return truncate(sb.toString(), limitChars);
    }

    private static String extractRarText(File file, int limitChars, OllamaClient ollamaClient, String ocrModel) {
        StringBuilder sb = new StringBuilder();
        try (Archive archive = new Archive(file)) {
            if (archive.isEncrypted() || archive.isPasswordProtected()) {
                logger.info("RAR archive '{}' is password protected or encrypted, skipping.", file.getName());
                return "";
            }
            int entryCount = 0;
            long totalBytesRead = 0;
            for (FileHeader fh : archive.getFileHeaders()) {
                if (fh.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    logger.warn("RAR archive '{}' exceeded max entry count ({}), stopping extraction.", file.getName(), MAX_ARCHIVE_ENTRIES);
                    break;
                }
                String entryName = fh.getFileName();
                if (entryName == null || entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    logger.warn("Skipping RAR entry with potential path traversal: {}", entryName);
                    continue;
                }
                String ext = getExtension(entryName);
                if (!INNER_SUPPORTED_EXTENSIONS.contains(ext)) {
                    continue;
                }
                if (fh.getFullUnpackSize() > MAX_ENTRY_SIZE_BYTES) {
                    logger.warn("RAR entry '{}' uncompressed size ({} bytes) exceeds limit, skipping.", entryName, fh.getFullUnpackSize());
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream is = archive.getInputStream(fh)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    long entryBytesRead = 0;
                    while ((n = is.read(buffer)) != -1) {
                        entryBytesRead += n;
                        totalBytesRead += n;
                        if (entryBytesRead > MAX_ENTRY_SIZE_BYTES || totalBytesRead > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                            logger.warn("RAR entry '{}' exceeded uncompressed byte limits, skipping rest.", entryName);
                            break;
                        }
                        baos.write(buffer, 0, n);
                    }
                }

                byte[] entryBytes = baos.toByteArray();
                String text = extractInnerEntryText(entryName, ext, entryBytes, limitChars, ollamaClient, ocrModel);
                if (text != null && !text.trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append("[Archive: ").append(entryName).append("]\n").append(text.trim());
                    if (sb.length() >= limitChars) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn("Non-fatal error reading RAR archive '{}': {}", file.getName(), t.getMessage());
        }
        return truncate(sb.toString(), limitChars);
    }

    private static String extractInnerEntryText(String entryName, String ext, byte[] entryBytes, int limitChars,
                                                OllamaClient ollamaClient, String ocrModel) {
        if (entryBytes == null || entryBytes.length == 0) {
            return "";
        }
        try {
            if ("pdf".equals(ext)) {
                try (PDDocument document = PDDocument.load(new ByteArrayInputStream(entryBytes))) {
                    if (document.isEncrypted()) {
                        return "";
                    }
                    PDFTextStripper stripper = new PDFTextStripper();
                    return truncate(stripper.getText(document), limitChars);
                }
            } else if ("docx".equals(ext)) {
                try (InputStream bais = new ByteArrayInputStream(entryBytes)) {
                    return extractDocxStream(bais, limitChars);
                }
            } else if ("xlsx".equals(ext)) {
                try (InputStream bais = new ByteArrayInputStream(entryBytes)) {
                    return extractXlsxStream(bais, limitChars);
                }
            } else if (IMAGE_EXTENSIONS.contains(ext)) {
                if (ollamaClient == null) {
                    return "";
                }
                return extractImageOcr(entryBytes, entryName, limitChars, ollamaClient, ocrModel);
            } else {
                // txt, csv, md, log
                return SmartCharsetDetector.decode(entryBytes, limitChars);
            }
        } catch (Throwable t) {
            logger.debug("Failed to extract text from archive entry '{}': {}", entryName, t.getMessage());
            return "";
        }
    }

    private static String extractImageOcr(byte[] imageBytes, String fileName, int limitChars,
                                          OllamaClient ollamaClient, String ocrModel) {
        if (ollamaClient == null || imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        try {
            byte[] processedBytes = preprocessImageForOcr(imageBytes, fileName);
            String systemPrompt = "You are a professional OCR engine. Extract and transcribe all readable text, printed words, handwriting, tables, and error messages from this image verbatim. Output ONLY the extracted plain text directly without any explanation or conversational response.";
            String userPrompt = "Transcribe all text from this image.";
            String extracted = ollamaClient.chatWithImage(systemPrompt, userPrompt, processedBytes, ocrModel);
            if (extracted == null) {
                return "";
            }
            return truncate(extracted.trim(), limitChars);
        } catch (Throwable t) {
            logger.warn("OCR failed for image '{}': {}", fileName, t.getMessage());
            return "";
        }
    }

    static byte[] preprocessImageForOcr(byte[] imageBytes, String fileName) {
        if (imageBytes == null || imageBytes.length == 0) {
            return imageBytes;
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                return imageBytes;
            }
            int width = original.getWidth();
            int height = original.getHeight();
            boolean isGif = fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".gif");

            if (width > MAX_IMAGE_OCR_DIMENSION || height > MAX_IMAGE_OCR_DIMENSION || isGif) {
                double scale = Math.min((double) MAX_IMAGE_OCR_DIMENSION / width, (double) MAX_IMAGE_OCR_DIMENSION / height);
                if (scale > 1.0) {
                    scale = 1.0;
                }
                int newWidth = Math.max(1, (int) Math.round(width * scale));
                int newHeight = Math.max(1, (int) Math.round(height * scale));

                BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, newWidth, newHeight);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(original, 0, 0, newWidth, newHeight, null);
                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resized, "jpg", baos);
                byte[] convertedBytes = baos.toByteArray();
                logger.debug("Preprocessed image '{}' ({}x{}) -> {}x{} JPEG ({} bytes -> {} bytes)",
                        fileName, width, height, newWidth, newHeight, imageBytes.length, convertedBytes.length);
                return convertedBytes;
            }
            return imageBytes;
        } catch (Throwable t) {
            logger.debug("Image preprocessing for OCR bypassed: {}", t.getMessage());
            return imageBytes;
        }
    }

    private static String extractPlainText(File file, int maxChars) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return SmartCharsetDetector.decode(bytes, maxChars);
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (maxChars > 0 && trimmed.length() > maxChars) {
            return trimmed.substring(0, maxChars).trim();
        }
        return trimmed;
    }
}
