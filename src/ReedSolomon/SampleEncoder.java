package ReedSolomon;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Command-line program encodes one file using Reed-Solomon 4+2.
 *
 * The one argument should be a file name, say "foo.txt".  This program
 * will create six files in the same directory, breaking the input file
 * into four data shards, and two parity shards.  The output files are
 * called "foo.txt.0", "foo.txt.1", ..., and "foo.txt.5".  Numbers 4
 * and 5 are the parity shards.
 *
 * The data stored is the file size (four byte int), followed by the
 * contents of the file, and then padded to a multiple of four bytes
 * with zeros.  The padding is because all four data shards must be
 * the same size.
 */

public class SampleEncoder {

	public static final int DATA_SHARDS = 223;
    public static final int PARITY_SHARDS = 32;
    public static final int TOTAL_SHARDS = 255;

    public static final int BYTES_IN_INT = 4;
    
    @SuppressWarnings("resource")
	public static void main(String [] arguments) throws IOException {

    	String filename="C:\\Users\\Administrator\\Desktop\\testingfile.txt";

        final File inputFile = new File(filename);
        if (!inputFile.exists()) {
            System.out.println("Cannot read input file: " + inputFile);
            return;
        }

        // Get the size of the input file.  (Files bigger that
        // Integer.MAX_VALUE will fail here!)
        final int fileSize = (int) inputFile.length();

        // Figure out how big each shard will be.  The total size stored
        // will be the file size (8 bytes) plus the file.
        final int storedSize = fileSize + BYTES_IN_INT;
        final int shardSize = (storedSize + DATA_SHARDS - 1) / DATA_SHARDS;
        
        System.out.println("This is the storedSize:"+storedSize);

        // Create a buffer holding the file size, followed by
        // the contents of the file.
        final int bufferSize = shardSize * DATA_SHARDS;
        final byte [] allBytes = new byte[bufferSize];
        ByteBuffer.wrap(allBytes).putInt(fileSize);
        InputStream in = new FileInputStream(inputFile);
        int bytesRead = in.read(allBytes, BYTES_IN_INT, fileSize);
        if (bytesRead != fileSize) {
            throw new IOException("not enough bytes read");
        }
        in.close();
        
        System.out.println("This is the shardSize:"+shardSize);
        // Make the buffers to hold the shards.
        byte [] [] shards = new byte [TOTAL_SHARDS] [shardSize];

        
        // Fill in the data shards
        for (int i = 0; i < DATA_SHARDS; i++) {
            System.arraycopy(allBytes, i * shardSize, shards[i], 0, shardSize);
        }
        System.out.println("This is the original data(Before RScodeing).");
        for(int i=0;i<DATA_SHARDS;i++){
        	for(int j=0;j<shards[i].length;j++){
        		System.out.print(String.format("%02x ", shards[i][j]));
        		System.out.print(" ");
        	}
        	System.out.println();
        }
        byte[][] parity;
        // Use Reed-Solomon to calculate the parity.
        ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
        parity = reedSolomon.encodeParitys(shards, 0, shardSize);

        //After RScoding,print the paritys
        System.out.println("This is the paritys.");
        for(int i=0;i<parity.length;i++){
        	for(int j=0;j<parity[i].length;j++){
        		System.out.print(String.format("%02x ", parity[i][j]));
        		System.out.print(" ");
        	}
        	System.out.println();
        }
        
    }
}
