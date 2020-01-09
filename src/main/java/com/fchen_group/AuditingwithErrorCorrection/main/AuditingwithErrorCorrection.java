package com.fchen_group.AuditingwithErrorCorrection.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import com.javamex.classmexer.MemoryUtil;

import com.fchen_group.AuditingwithErrorCorrection.ReedSolomon.Galois;
import com.fchen_group.AuditingwithErrorCorrection.ReedSolomon.ReedSolomon;

public class AuditingwithErrorCorrection {
    private final int DATA_SHARDS;             //message length k
    private final int PARITY_SHARDS;           //Amount of fault tolerance n-k
    private final int SHARD_NUMBER;             //The total number of data blocks F/n

    private long fileSize;
    private long storeSize;
    private String Key;
    private String sKey;
    private String file;

    public final int BYTES_IN_INT = 4;
    public byte[][] paritys;                  //paritys
    public byte[][] originaldata;              //The source data
    public static int len = 16;                     //Security parameter, here only the character length of the key

    public AuditingwithErrorCorrection(String filepath, int n, int k) throws IOException {
        this.file = filepath;
        this.DATA_SHARDS = k;
        this.PARITY_SHARDS = n - k;
        File inputFile = new File(this.file);
        // Get the size of the input file.  (Files bigger that Integer.MAX_VALUE will fail here!)
        this.fileSize = inputFile.length();

        // Figure out how big each shard will be.The total size stored will be the file size (8 bytes) plus the file.
        this.storeSize = fileSize + BYTES_IN_INT;
        System.out.println("The storedSize is:" + storeSize + " Bytes.");

        this.SHARD_NUMBER = (Integer.parseInt(String.valueOf(storeSize)) + DATA_SHARDS - 1) / DATA_SHARDS;
        System.out.println("The number of data shards is:" + SHARD_NUMBER + ".");

        //The source data, Each row represents an m_i,Where the last n-k bytes of each line are the corresponding paritys
        this.originaldata = new byte[SHARD_NUMBER][DATA_SHARDS];
        FileInputStream in = new FileInputStream(inputFile);
        // Fill in the data shards
        for (int i = 0; i < SHARD_NUMBER; i++) {
            in.read(originaldata[i]);
        }
        in.close();
    }

    /**
     * Key generation function, executed only once
     *
     * @return
     */
    public void generateKey() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuffer sBuffer = new StringBuffer();
        Random random = new Random();
        for (int i = 0; i < len; i++) {
            sBuffer.append(chars.charAt(random.nextInt(chars.length())));
        }
        //System.out.println("This is keyPRF:"+sBuffer.toString());
        //return sBuffer.toString();
        Random rand = new Random();
        String valString = "";
        for (int i = 0; i < this.PARITY_SHARDS; i++) {
            valString += chars.charAt(rand.nextInt(chars.length()));
        }
        this.sKey = valString;
        this.Key = sBuffer.toString();
    }

    //Error-correcting coding of the source data
    public void Outsource() {
        this.paritys = new byte[SHARD_NUMBER][];
        ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
        for (int i = 0; i < SHARD_NUMBER; i++) {
            //Error correction coding is performed for each m_i
            paritys[i] = reedSolomon.encodeParity(originaldata[i], 0, 1);
        }

        //Add a secret key
        for (int j = 0; j < paritys.length; j++) {
            byte[] spkey = sKey.getBytes();
            if (spkey.length != paritys[j].length) {
                System.out.println("Error:The length of s is inconsistent with that of paritys.length.");
            } else {
                for (int i = 0; i < paritys[j].length; i++) {
                    paritys[j][i] = Galois.multiply(paritys[j][i], spkey[i]);
                }
            }
        }

        //Add a pseudo random number
        for (int i = 0; i < paritys.length; i++) {
            byte[] randoms = PseudoRandom.generateRandom(i, this.Key, PARITY_SHARDS);
            //System.out.println("Index:"+i+" this length:"+paritys[i].length);
            //For each parity adding a pseudo random number
            for (int j = 0; j < PARITY_SHARDS; j++) {
                //System.out.print(String.format("%02x ", randoms[j]));
                paritys[i][j] = Galois.add(paritys[i][j], randoms[j]);
            }
            //System.out.println("");
        }
    }

    /**
     * Generate audit indexes and coefficients based on challenge length
     *
     * @param challengeLen
     * @return
     */
    public ChallengeData audit(int challengeLen) {
        //coefficients/GF(2^8),randomly generate 8 bits of random Numbers
        byte[] coefficients = new byte[challengeLen];
        //indexes
        int[] index = new int[challengeLen];
        Random random = new Random();
        for (int i = 0; i < challengeLen; i++) {
            //SHARD_NUMBER is the number of blocks (m_i)
            index[i] = random.nextInt(SHARD_NUMBER);
        }
        random.nextBytes(coefficients);

        return new ChallengeData(index, coefficients);
    }

    /**
     * When the server receives the chal, it calculates the corresponding Proof
     *
     * @param challenge
     * @return
     */
    public ProofData prove(ChallengeData challenge) {
        byte[] dataproof = new byte[DATA_SHARDS];
        byte[] parityproof = new byte[PARITY_SHARDS];

        for (int i = 0; i < challenge.coefficients.length; i++) {
            //The index of the data block being audited
            int j = challenge.index[i];
            byte[] tempm = new byte[DATA_SHARDS];
            byte[] tempp = new byte[PARITY_SHARDS];
            //Calculate c_j . m_ij
            for (int k = 0; k < DATA_SHARDS; k++) {
                tempm[k] = Galois.multiply(challenge.coefficients[i], originaldata[j][k]);
            }
            //Calculate c_j . σ_ij
            for (int k = 0; k < PARITY_SHARDS; k++) {
                tempp[k] = Galois.multiply(challenge.coefficients[i], paritys[j][k]);
            }

            for (int k = 0; k < DATA_SHARDS; k++) {
                dataproof[k] = Galois.add(dataproof[k], tempm[k]);
            }
            for (int k = 0; k < PARITY_SHARDS; k++) {
                parityproof[k] = Galois.add(parityproof[k], tempp[k]);
            }
        }

        return new ProofData(dataproof, parityproof);
    }

    /**
     * Verify the correctness of proof returned from the cloud
     *
     * @param challenge
     * @param proof
     * @return
     */
    public boolean verify(ChallengeData challenge, ProofData proof) {
        byte[] verifyparity = new byte[PARITY_SHARDS];
        byte[] recomputeparity = new byte[PARITY_SHARDS];

        //Storage the sum of c_j . AES(i_j)
        byte[] sumtemp = new byte[PARITY_SHARDS];
        for (int i = 0; i < challenge.index.length; i++) {
            //Generate AES(i_j) based on the index and key
            byte[] AESRandom = PseudoRandom.generateRandom(challenge.index[i], this.Key, PARITY_SHARDS);
            //System.out.println("Index:"+challenge.index[i]+" this length:"+PARITY_SHARDS);
            //The length of the AESRandom[] might be longer than PARITY_SHARDS, here only the PARITY_SHARDS byte is needed
            byte[] temp = new byte[PARITY_SHARDS];
            for (int j = 0; j < PARITY_SHARDS; j++) {
                //System.out.print(String.format("%02x ", AESRandom[j]));
                //Calculate c_j . AES(i_j)
                temp[j] = Galois.multiply(challenge.coefficients[i], AESRandom[j]);
            }
            //Calculate the sum of c_j . AES(i_j)
            for (int k = 0; k < PARITY_SHARDS; k++) {
                sumtemp[k] = Galois.add(sumtemp[k], temp[k]);
            }
        }

        // the parity for verification
        for (int i = 0; i < PARITY_SHARDS; i++) {
            verifyparity[i] = Galois.subtract(proof.parityproof[i], sumtemp[i]);
        }

        //Divided by the secret key s
        for (int j = 0; j < PARITY_SHARDS; j++) {
            byte[] spkey = sKey.getBytes();
            verifyparity[j] = Galois.divide(verifyparity[j], spkey[j]);
        }

        //Re-encode the returned data
        ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
        recomputeparity = reedSolomon.encodeParity(proof.dataproof, 0, 1);

        //Determine if the two parity arrays are equal
        //System.out.println("This is result:"+comparebyteArray(verifyparity, recomputeparity));
        return comparebyteArray(verifyparity, recomputeparity);
    }

    //Printing a two-dimensional array
    public void print(byte[][] data) {
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                System.out.print(String.format("%02x ", data[i][j]));
                System.out.print(" ");
            }
            System.out.println();
        }
        System.out.println();
    }

    //Compare two byte arrays to see if they are equal
    public boolean comparebyteArray(byte[] a, byte[] b) {
        if (a == null) {
            return (b == null);
        }
        if (b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        if (!Arrays.equals(a, b)) {
            return false;
        }
        return true;
    }

    /**
     * Used to test storage performance
     *
     * @return
     */
    public long getAllStorage() {
//		long result=((8-(12+paritys.length*4)%8)+(12+paritys.length*4))+paritys.length*((8-(12+paritys[0].length)%8)+(12+paritys[0].length));
//		//return paritys.length*paritys[0].length;
//		System.out.println("This is the storage(caqulate):"+result);
//		return result;
        return MemoryUtil.deepMemoryUsageOf(paritys);
    }
}
