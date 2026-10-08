package info.jtrac.lucene;

import info.jtrac.domain.Attachment;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.Space;
import info.jtrac.util.AttachmentTextExtractor;
import info.jtrac.util.AttachmentUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AttachmentIndexingIntegrationTest {

    private ApplicationContext context;
    private File tempHome;
    private Indexer indexer;
    private IndexSearcher searcher;

    @Before
    public void setUp() throws Exception {
        tempHome = Files.createTempDirectory("jtrac-lucene-test-").toFile();
        tempHome.deleteOnExit();

        File indexesDir = new File(tempHome, "indexes");
        indexesDir.mkdirs();
        File attachmentsDir = new File(tempHome, "attachments");
        attachmentsDir.mkdirs();

        System.setProperty("jtrac.home", tempHome.getAbsolutePath());
        context = new FileSystemXmlApplicationContext("src/main/webapp/WEB-INF/applicationContext-lucene.xml");
        indexer = (Indexer) context.getBean("indexer");
        searcher = (IndexSearcher) context.getBean("indexSearcher");
    }

    @Test
    public void testAttachmentTextIndexingAndSearchHits() throws Exception {
        long spaceId = 1L;
        Space space = new Space();
        space.setId(spaceId);
        space.setPrefixCode("PROJ");

        Item item1 = new Item();
        item1.setId(100L);
        item1.setSpace(space);
        item1.setSummary("Issue for text attachment");
        item1.setDetail("Please see attached notes");

        // 1. Text attachment
        Attachment attText = new Attachment();
        attText.setId(1L);
        attText.setFilePrefix(1L);
        attText.setFileName("notes.txt");

        File textFile = AttachmentUtils.getAttachmentFileForWrite(attText, spaceId, tempHome.getAbsolutePath());
        Files.write(textFile.toPath(), "ElasticSearch is great but LuceneCore is embedded and lightweight".getBytes(StandardCharsets.UTF_8));

        History history1 = new History(item1);
        history1.setId(10L);
        history1.setParent(item1);
        history1.setAttachment(attText);

        String text1 = AttachmentTextExtractor.extractText(textFile, 10, 50000);
        history1.setAttachmentText(text1);

        indexer.index(item1);
        indexer.index(history1);

        // 2. PDF attachment
        Item item2 = new Item();
        item2.setId(200L);
        item2.setSpace(space);
        item2.setSummary("Issue for PDF attachment");
        item2.setDetail("Please see attached specification");

        Attachment attPdf = new Attachment();
        attPdf.setId(2L);
        attPdf.setFilePrefix(2L);
        attPdf.setFileName("spec.pdf");

        File pdfFile = AttachmentUtils.getAttachmentFileForWrite(attPdf, spaceId, tempHome.getAbsolutePath());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                stream.showText("DeepMind Antigravity Architecture Specification");
                stream.endText();
            }
            doc.save(pdfFile);
        }

        History history2 = new History(item2);
        history2.setId(20L);
        history2.setParent(item2);
        history2.setAttachment(attPdf);

        String text2 = AttachmentTextExtractor.extractText(pdfFile, 10, 50000);
        history2.setAttachmentText(text2);

        indexer.index(item2);
        indexer.index(history2);

        // 3. OpenXML .docx attachment
        Item item3 = new Item();
        item3.setId(300L);
        item3.setSpace(space);
        item3.setSummary("Issue for Word document");

        Attachment attDocx = new Attachment();
        attDocx.setId(3L);
        attDocx.setFilePrefix(3L);
        attDocx.setFileName("contract.docx");

        File docxFile = AttachmentUtils.getAttachmentFileForWrite(attDocx, spaceId, tempHome.getAbsolutePath());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String docXml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                    + "<w:body><w:p><w:r><w:t>QuantumComputing breakthrough milestone achieved</w:t></w:r></w:p></w:body></w:document>";
            zos.write(docXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(docxFile.toPath(), baos.toByteArray());

        History history3 = new History(item3);
        history3.setId(30L);
        history3.setParent(item3);
        history3.setAttachment(attDocx);

        String text3 = AttachmentTextExtractor.extractText(docxFile, 10, 50000);
        history3.setAttachmentText(text3);

        indexer.index(item3);
        indexer.index(history3);

        // 4. Verify Lucene search hits
        List<Long> hits1 = searcher.findItemIdsContainingText("lightweight");
        Assert.assertEquals(1, hits1.size());
        Assert.assertEquals(Long.valueOf(100L), hits1.get(0));

        List<Long> hits2 = searcher.findItemIdsContainingText("Antigravity");
        Assert.assertEquals(1, hits2.size());
        Assert.assertEquals(Long.valueOf(200L), hits2.get(0));

        List<Long> hits3 = searcher.findItemIdsContainingText("QuantumComputing");
        Assert.assertEquals(1, hits3.size());
        Assert.assertEquals(Long.valueOf(300L), hits3.get(0));

        // 5. Search for attachment filename itself
        List<Long> fileHits = searcher.findItemIdsContainingText("notes.txt");
        Assert.assertEquals(1, fileHits.size());
        Assert.assertEquals(Long.valueOf(100L), fileHits.get(0));

        // 6. Test update indexing (no duplicate hits when re-indexed)
        indexer.index(history1);
        List<Long> deduplicatedHits = searcher.findItemIdsContainingText("lightweight");
        Assert.assertEquals(1, deduplicatedHits.size());
    }

    @Test
    public void testBlacklistFileExclusion() {
        File dummyDoc = new File(tempHome, "attachments/1/99_legacy.doc");
        dummyDoc.getParentFile().mkdirs();
        try {
            Files.write(dummyDoc.toPath(), "Legacy binary Word content".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        String extracted = AttachmentTextExtractor.extractText(dummyDoc, 10, 50000);
        Assert.assertEquals("", extracted);
    }

    @Test
    public void testZipAttachmentIndexingAndSearchHits() throws Exception {
        long spaceId = 1L;
        Space space = new Space();
        space.setId(spaceId);
        space.setPrefixCode("PROJ");

        Item item4 = new Item();
        item4.setId(400L);
        item4.setSpace(space);
        item4.setSummary("Issue for ZIP archive attachment");
        item4.setDetail("Please see attached archive bundle");

        Attachment attZip = new Attachment();
        attZip.setId(4L);
        attZip.setFilePrefix(4L);
        attZip.setFileName("archive_bundle.zip");

        File zipFile = AttachmentUtils.getAttachmentFileForWrite(attZip, spaceId, tempHome.getAbsolutePath());
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("invoice_report.txt"));
            zos.write("SpecialInvoiceIdentifier2026 Amount USD 8888".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        History history4 = new History(item4);
        history4.setId(40L);
        history4.setParent(item4);
        history4.setAttachment(attZip);

        String text4 = AttachmentTextExtractor.extractText(zipFile, 10, 50000);
        history4.setAttachmentText(text4);

        indexer.index(item4);
        indexer.index(history4);

        List<Long> hits = searcher.findItemIdsContainingText("SpecialInvoiceIdentifier2026");
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(Long.valueOf(400L), hits.get(0));
    }
}
