package com.github.hmdev.image;

import com.github.hmdev.info.ImageInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class ImageInfoReaderTest {
    @Test
    public void loadZipImageInfos_fromTestDataImg_shouldCollectEntries() throws Exception {
        File zip = new File("test_data/img/test.zip");
        if (!zip.exists()) {
            // Skip if sample zip is not available; create a minimal empty assertion
            return;
        }
        ImageInfoReader reader = new ImageInfoReader(false, zip);
        reader.loadZipImageInfos(zip, true);
        // Verify that some entries were collected
        Assert.assertTrue("Expected imageFileInfos to have entries", reader.imageFileInfos.size() > 0);
        // Check that metadata contains width/height for first entry
        String any = reader.imageFileInfos.keySet().iterator().next();
        ImageInfo info = reader.imageFileInfos.get(any);
        Assert.assertTrue("width should be > 0", info.getWidth() > 0);
        Assert.assertTrue("height should be > 0", info.getHeight() > 0);
    }
}
