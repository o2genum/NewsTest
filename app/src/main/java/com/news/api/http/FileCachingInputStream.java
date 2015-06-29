package com.news.api.http;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * InputStream decorator saving read bytes to a file.
 */
public class FileCachingInputStream extends InputStream {

    private final int BUFFER_SIZE = 8192;

    private InputStream decorated;
    private FileOutputStream toFile;

    public FileCachingInputStream(InputStream is, File file) throws IOException {
        decorated = is;

        file.createNewFile();
        toFile = new FileOutputStream(file);
    }

    @Override
    public int read() throws IOException {
        int r = decorated.read();
        if (r != -1) {
            toFile.write(r);
        }
        return r;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int read = decorated.read(buffer, 0, buffer.length);
        if (read != -1) {
            toFile.write(buffer, 0, read);
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        decorated.close();
        toFile.close();
    }
}
