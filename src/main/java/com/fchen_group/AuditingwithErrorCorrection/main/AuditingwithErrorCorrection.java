package com.fchen_group.AuditingwithErrorCorrection.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.GetObjectRequest;
import com.javamex.classmexer.MemoryUtil;

import com.fchen_group.AuditingwithErrorCorrection.ReedSolomon.Galois;
import com.fchen_group.AuditingwithErrorCorrection.ReedSolomon.ReedSolomon;

public class AuditingwithErrorCorrection {
    private final int DATA_SHARDS;             //message length k
    private final int PARITY_SHARDS;           //Amount of fault tolerance n-k
    private final int SHARD_NUMBER;             //The total number of data blocks F/n

    private Key key;

    public final int BYTES_IN_INT = 4;
    public byte[][] paritys;                  //paritys
    public byte[][] originaldata;              //The source data
    public static int len = 16;                     //Security parameter, here only the character length of the key

    public AuditingwithErrorCorrection(String filepath, int n, int k) throws IOException {
        this.DATA_SHARDS = k;
        this.PARITY_SHARDS = n - k;

        // Get the size of the input file.  (Files bigger that Integer.MAX_VALUE will fail here!)
        File inputFile = new File(filepath);
        long fileSize = inputFile.length();

        // Figure out how big each shard will be.The total size stored will be the file size (8 bytes) plus the file.
        long storeSize = fileSize + BYTES_IN_INT;
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
     */
    public Key keyGen() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // generate k
        Random kRandom = new Random();
        StringBuilder k = new StringBuilder();
        for (int i = 0; i < len; i++) {
            k.append(chars.charAt(kRandom.nextInt(chars.length())));
        }

        // generate s
        Random sRandom = new Random();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < this.PARITY_SHARDS; i++) {
            s.append(chars.charAt(sRandom.nextInt(chars.length())));
        }

        key = new Key(k.toString(), s.toString());
        return key;
    }

    /**
     * Error-correcting coding of the source data
     */
    public void outsource() {
        this.paritys = new byte[SHARD_NUMBER][];
        ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
        for (int i = 0; i < SHARD_NUMBER; i++) {
            // Error correction coding is performed for each m_i
            paritys[i] = reedSolomon.encodeParity(originaldata[i], 0, 1);
        }

        // Add a secret key
        for (int i = 0; i < paritys.length; i++) {
            byte[] spkey = key.s.getBytes();
            if (spkey.length != paritys[i].length) {
                System.out.println("Error:The length of s is inconsistent with that of paritys.length.");
                continue;
            }
            for (int j = 0; j < paritys[i].length; j++) {
                paritys[i][j] = Galois.multiply(paritys[i][j], spkey[j]);
            }
        }

        // Add a pseudo random number
        for (int i = 0; i < paritys.length; i++) {
            byte[] randoms = PseudoRandom.generateRandom(i, key.k, PARITY_SHARDS);
            // For each parity adding a pseudo random number
            for (int j = 0; j < PARITY_SHARDS; j++) {
                paritys[i][j] = Galois.add(paritys[i][j], randoms[j]);
            }
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
     * When the server receives the chal, it calculates the corresponding Proof
     * Get file data from COS
     *
     * @param challenge
     * @return
     */
    public ProofData prove(ChallengeData challenge, String COSConfigFilePath, String cloudFileName) {
        byte[] dataproof = new byte[DATA_SHARDS];
        byte[] parityproof = new byte[PARITY_SHARDS];

        // initial cos
        // 从配置文件获取 secretId, secretKey
        String secretId = "";
        String secretKey = "";
        try {
            FileInputStream propertiesFIS = new FileInputStream(COSConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            secretId = properties.getProperty("secretId");
            secretKey = properties.getProperty("secretKey");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 初始化用户身份信息（secretId, secretKey）。
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置 bucket 的区域
        Region region = new Region("ap-chengdu");
        ClientConfig clientConfig = new ClientConfig(region);
        // 生成 cos 客户端。
        COSClient cosClient = new COSClient(cred, clientConfig);
        // 设置 BucketName-APPID
        String bucketName = "crypto2019-1254094112";
        // 声明下载文件所需变量
        GetObjectRequest getObjectRequest;
        COSObject cosObject;
        InputStream cloudFileIn;

        for (int i = 0; i < challenge.coefficients.length; i++) {
            //The index of the data block being audited
            int j = challenge.index[i];
            if (i % 10 == 0)
                System.out.printf("Round %d: block index = %d\n", i, j);

            // get original data from cloud
            byte[] originalDataCloud = new byte[DATA_SHARDS];
            getObjectRequest = new GetObjectRequest(bucketName, cloudFileName);
            getObjectRequest.setRange(DATA_SHARDS * j, DATA_SHARDS * (j + 1) - 1);
            cosObject = cosClient.getObject(getObjectRequest);
            cloudFileIn = cosObject.getObjectContent();
            try {
                for (int n = 0; n != -1; ) {
                    n = cloudFileIn.read(originalDataCloud, 0, originalDataCloud.length);
                }
                cloudFileIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // get paritys data from cloud
            byte[] paritysCloud = new byte[PARITY_SHARDS];
            getObjectRequest = new GetObjectRequest(bucketName, cloudFileName + ".paritys");
            getObjectRequest.setRange(PARITY_SHARDS * j, PARITY_SHARDS * (j + 1) - 1);
            cosObject = cosClient.getObject(getObjectRequest);
            cloudFileIn = cosObject.getObjectContent();
            try {
                for (int n = 0; n != -1; ) {
                    n = cloudFileIn.read(paritysCloud, 0, paritysCloud.length);
                }
                cloudFileIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            byte[] tempm = new byte[DATA_SHARDS];
            byte[] tempp = new byte[PARITY_SHARDS];
            //Calculate c_j . m_ij
            for (int k = 0; k < DATA_SHARDS; k++) {
                if (originaldata[j][k] != originalDataCloud[k]) System.out.println("====== Different ! ======");
                tempm[k] = Galois.multiply(challenge.coefficients[i], originalDataCloud[k]);  // originaldata[j][k] == originalDataCloud[k]
            }
            //Calculate c_j . σ_ij
            for (int k = 0; k < PARITY_SHARDS; k++) {
                if (paritys[j][k] != paritysCloud[k]) System.out.println("====== Different ! ======");
                tempp[k] = Galois.multiply(challenge.coefficients[i], paritysCloud[k]);  // paritys[j][k] == paritysCloud[k]
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
            byte[] AESRandom = PseudoRandom.generateRandom(challenge.index[i], key.k, PARITY_SHARDS);
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
            byte[] spkey = key.s.getBytes();
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

    public int getSHARD_NUMBER() {
        return SHARD_NUMBER;
    }

    public int getPARITY_SHARDS() {
        return PARITY_SHARDS;
    }

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }
}
