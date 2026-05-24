package org.juke.framework.storage;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;


public class ZipFileUtilTest {
    @Test
    public void testZipFileUtil() {        ZipUtil zipFileUtil = new ZipUtil("C:/temp","test.zip");
        HashMap<String,String> map = new HashMap<String,String>();
        map.put("test","test");
        map.put("test2","test2");
        File file = new File("C:/temp/test.zip");
        file.delete();
        try {
            zipFileUtil.createZipFile("C:/temp/test.zip", map);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //check if test and test2 are in the zip file
        try {
            Set<String> fileNames = ZipUtil.getFileNamesFromZipFile("C:/temp/test.zip");

            assert(fileNames.size() == 2);
            assert(fileNames.contains("test"));
            assert(fileNames.contains("test2"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //assert file exists
        assert(file.exists());

        //read file test from test.zip
        try {
            String fileContent = zipFileUtil.readStringFromZipFile("C:/temp/test.zip", "test");
            assert(fileContent.equals("test"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            String fileContent = zipFileUtil.readStringFromZipFile("C:/temp/test.zip", "test2");
            assert(fileContent.equals("test2"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        new File("C:/temp/test2.zip").delete();

        try {
            zipFileUtil.copyFile(new File("C:/temp/test.zip"), new File("C:/temp/test2.zip"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assert(new File("C:/temp/test2.zip").exists());
        try {
            Set<String> fileNames = ZipUtil.getFileNamesFromZipFile("C:/temp/test2.zip");

            assert(fileNames.size()==2);
            assert(fileNames.contains("test"));
            assert(fileNames.contains("test2"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally{
            // delete zip files
            new File("C:/temp/test.zip").delete();
            new File("C:/temp/test2.zip").delete();
        }
        // delete zip files



    }

}
