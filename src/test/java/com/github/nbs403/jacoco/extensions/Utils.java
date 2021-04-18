package com.github.nbs403.jacoco.extensions;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Utils {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void unzipFileToFolder(final String zipFileName, final File targetFolder) throws URISyntaxException, IOException {
        final URI path = Objects.requireNonNull(Utils.class.getClassLoader().getResource(zipFileName)).toURI();
        final File inputFile = new File(path);
        final ZipFile zipFile = new ZipFile(inputFile);
        final ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }
        ZipEntry entry = zis.getNextEntry();
        while (entry != null) {
            final File outFile = targetFolder.toPath().resolve(entry.getName()).toFile();
            if (!entry.isDirectory()) {
                FileUtils.writeByteArrayToFile(new File(outFile.getAbsolutePath()),
                                               IOUtils.toByteArray(zipFile.getInputStream(entry)));
            } else {
                outFile.mkdirs();
            }
            entry = zis.getNextEntry();
        }
        zipFile.close();
    }

}