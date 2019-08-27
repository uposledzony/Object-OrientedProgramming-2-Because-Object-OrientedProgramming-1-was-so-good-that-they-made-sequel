package LimakWebApp.DataPackets;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;

/**
 * <h1>FilePacket</h1>
 * This class is a wrapper of file and owner assigned to file.
 * @author  Kamil Chrustowski
 * @version 1.0
 * @since   09.08.2019
 */
public class FilePacket implements Serializable {

    private String userName;
    private byte[] fileBytes;
    private String fileName;
    private long size;

    /**
     * Basic constructor of {@link FilePacket}. Reads data
     * @param userName indicates the owner of file
     * @param fileName indicates the file name
     * @param directoryPath indicates the path do directory, where file item is stored
     */
    public FilePacket(String userName, String fileName, String directoryPath) {
        this.userName = userName;
        this.fileName = fileName;
        StringBuilder stringBuilder = new StringBuilder(directoryPath);
        stringBuilder.append('/').append(fileName);
        File file = new File(stringBuilder.toString());
        try (FileInputStream fileReader = new FileInputStream(file)) {
            fileBytes = fileReader.readAllBytes();
            size = file.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method returns user's name.
     * @return String
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Method returns content of the file as a byte array.
     * @return byte[]
     */
    public byte[] getFileBytes() {
        return fileBytes;
    }

    /**
     * Method returns file's name.
     * @return String
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Meethod returns size of file.
     * @return long
     */
    public long getSize() {
        return size;
    }
}