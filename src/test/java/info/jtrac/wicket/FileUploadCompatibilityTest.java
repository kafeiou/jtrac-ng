package info.jtrac.wicket;

import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletDiskFileUpload;
import org.apache.wicket.protocol.http.servlet.MultipartServletWebRequestImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression test for Wicket 10 Multipart upload with commons-fileupload2 and commons-io.
 * Ensures no IllegalAccessError or binary incompatibility on DiskFileItemFactory.builder().get().
 */
public class FileUploadCompatibilityTest {

    @Test
    public void testDiskFileItemFactoryInstantiation() {
        // This invocation triggers DiskFileItemFactory.<init>() -> AbstractStreamBuilder.getPath()
        // If commons-io is mismatched (e.g. <= 2.15.1 where getPath() is protected),
        // an IllegalAccessError is thrown.
        DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
        Assert.assertNotNull("DiskFileItemFactory must be successfully instantiated", factory);
    }

    @Test
    public void testDiskFileUploadInstantiation() {
        DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
        JakartaServletDiskFileUpload upload = new JakartaServletDiskFileUpload(factory);
        Assert.assertNotNull("JakartaServletDiskFileUpload must be successfully instantiated", upload);
        Assert.assertEquals(factory, upload.getFileItemFactory());
    }

    @Test
    public void testMultipartServletWebRequestClassReferencing() {
        // Ensure Wicket's MultipartServletWebRequestImpl class is present and linkable
        Assert.assertNotNull(MultipartServletWebRequestImpl.class);
    }
}
