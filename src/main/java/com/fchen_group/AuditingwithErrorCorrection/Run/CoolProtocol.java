package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class CoolProtocol {
    public static int magicNumber = 329;

    public int op;
    public int filenameLength;
    public int contentLength;
    public byte[] filename;
    public byte[] content;

    public CoolProtocol(int op, byte[] filename, byte[] content) {
        this.op = op;
        this.filenameLength = filename.length;
        this.contentLength = content.length;

        try {
            File file = new File(new String(filename));
            FileInputStream fileInputStream = new FileInputStream(file);
            this.content = new byte[(int) file.length()];
            this.contentLength = fileInputStream.read(this.content);
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            this.contentLength = content.length;
            this.content = content;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "SmartCarProtocol{" +
                "filenameLength=" + filenameLength +
                ", contentLength=" + contentLength +
                ", filename=" + new String(filename) +
                ", content=" + new String(content) +
                '}';
    }
}
