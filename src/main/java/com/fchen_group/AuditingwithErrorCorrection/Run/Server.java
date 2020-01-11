package com.fchen_group.AuditingwithErrorCorrection.Run;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

import java.io.*;
import java.util.Properties;

public class Server {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("show help");
        }

        String filePath = args[0];
        String propertiesFilePath = filePath + ".properties";
        String paritysFilePath = filePath + ".paritys";
        String challengeFilePath = filePath + ".challenge";
        String proofFilePath = filePath + ".proof";

        // get n and k
        FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
        Properties properties = new Properties();
        properties.load(propertiesFIS);
        propertiesFIS.close();
        int n = Integer.parseInt(properties.getProperty("n"));
        int k = Integer.parseInt(properties.getProperty("k"));
        // get challenge data
        ChallengeData challengeData = null;
        try {
            FileInputStream challengeFIS = new FileInputStream(challengeFilePath);
            ObjectInputStream in = new ObjectInputStream(challengeFIS);
            challengeData = (ChallengeData) in.readObject();
            in.close();
            challengeFIS.close();

            System.out.println("challenge");
            print(challengeData.coefficients);
        } catch (ClassNotFoundException e) {
            System.out.println("Class ChallengeData not found");
            e.printStackTrace();
            return;
        }
        // get paritys
        File paritysFile = new File(paritysFilePath);
        FileInputStream paritysFIS = new FileInputStream(paritysFile);
        AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
        int SHARD_NUMBER = auditingwithErrorCorrection.getSHARD_NUMBER(),
            PARITY_SHARDS = auditingwithErrorCorrection.getPARITY_SHARDS();
        System.out.printf("SHARD_NUMBER: %d, PARITY_SHARDS: %d\n", SHARD_NUMBER, PARITY_SHARDS);
        auditingwithErrorCorrection.paritys = new byte[SHARD_NUMBER][PARITY_SHARDS];
        for (int i = 0; i < SHARD_NUMBER; i++) {
            //按行存储，一行相当与一个parity块
            paritysFIS.read(auditingwithErrorCorrection.paritys[i], 0, PARITY_SHARDS);
        }
        paritysFIS.close();

        // calc Proof
        ProofData proof = null;
        proof = auditingwithErrorCorrection.prove(challengeData);

        // store Proof
        FileOutputStream proofFOS = new FileOutputStream(proofFilePath);
        ObjectOutputStream out = new ObjectOutputStream(proofFOS);
        out.writeObject(proof);
        out.close();
        proofFOS.close();

        System.out.println("proof");
        print(proof.dataproof);
        print(proof.parityproof);
    }

    public static void print(byte[] data) {
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[i]));
        }
        System.out.println();
    }

    public static void print(byte[][] data) {
        if (data == null)
            return;
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[0][i]));
        }
        System.out.println();
    }
}
