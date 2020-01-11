package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.*;
import java.util.Scanner;
import java.util.Properties;

import com.javamex.classmexer.MemoryUtil;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Client {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("show help");
        }

        String filePath = args[1];
        String propertiesFilePath = filePath + ".properties";
        String keyFilePath = filePath + ".key";
        String paritysFilePath = filePath + ".paritys";
        String challengeFilePath = filePath + ".challenge";
        String proofFilePath = filePath + ".proof";

        switch (args[0]) {
            case "outsource": {
                // parse args (n, k) and write them to properties
                int n = Integer.parseInt(args[2]), k = Integer.parseInt(args[3]);
                File propertiesFile = new File(propertiesFilePath);
                if (!propertiesFile.exists())
                    propertiesFile.createNewFile();
                FileOutputStream propertiesFOS = new FileOutputStream(propertiesFile);
                Properties properties = new Properties();
                properties.setProperty("n", String.valueOf(n));
                properties.setProperty("k", String.valueOf(k));
                properties.store(propertiesFOS, "n: the block length of Reed-Solomon codes\n" +
                        "k: the message length of Reed-Solomon codes k");
                propertiesFOS.close();

                // outsource
                AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
                auditingwithErrorCorrection.keyGen();
                auditingwithErrorCorrection.outsource();

                // store key
                File keyFile = new File(keyFilePath);
                if (!keyFile.exists())
                    keyFile.createNewFile();
                FileOutputStream keyFOS = new FileOutputStream(keyFile);
                Properties key = new Properties();
                key.setProperty("Key", auditingwithErrorCorrection.getKey());
                key.setProperty("sKey", auditingwithErrorCorrection.getsKey());
                key.store(keyFOS, "key = (Key, sKey)");
                keyFOS.close();

                System.out.println("outsource");
                print(auditingwithErrorCorrection.paritys);

                // write paritys to file
                File paritysFile = new File(paritysFilePath);
                if (!paritysFile.exists())
                    paritysFile.createNewFile();
                FileOutputStream paritysFOS = new FileOutputStream(paritysFile);
                for (int i = 0; i < auditingwithErrorCorrection.paritys.length; i++) {
                    //按行存储，一行相当与一个parity块
                    paritysFOS.write(auditingwithErrorCorrection.paritys[i]);
                }
                paritysFOS.close();
                break;
            }
            case "audit": {
                FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
                Properties properties = new Properties();
                properties.load(propertiesFIS);
                propertiesFIS.close();
                int n = Integer.parseInt(properties.getProperty("n"));
                int k = Integer.parseInt(properties.getProperty("k"));

                int challengeLen = 460;
                AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
                ChallengeData challengeData = auditingwithErrorCorrection.audit(challengeLen);

                System.out.println("challenge");
                print(challengeData.coefficients);

                // store challenge data
                FileOutputStream challengeFOS = new FileOutputStream(challengeFilePath);
                ObjectOutputStream out = new ObjectOutputStream(challengeFOS);
                out.writeObject(challengeData);
                out.close();
                challengeFOS.close();

                // proof = auditingwithErrorCorrection.prove(c);
                break;
            }
            case "verify": {
                boolean verifyResult = false;

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

                // get proof data
                ProofData proof = null;
                try {
                    FileInputStream proofFIS = new FileInputStream(proofFilePath);
                    ObjectInputStream in = new ObjectInputStream(proofFIS);
                    proof = (ProofData) in.readObject();
                    in.close();
                    proofFIS.close();

                    System.out.println("proof");
                    print(proof.dataproof);
                    print(proof.parityproof);
                } catch (ClassNotFoundException e) {
                    System.out.println("Class ChallengeData not found");
                    e.printStackTrace();
                    return;
                }

                // get key
                AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
                FileInputStream keyFIS = new FileInputStream(keyFilePath);
                Properties key = new Properties();
                key.load(keyFIS);
                keyFIS.close();
                String Key = key.getProperty("Key");
                String sKey = key.getProperty("sKey");
                auditingwithErrorCorrection.setKey(Key);
                auditingwithErrorCorrection.setsKey(sKey);

                verifyResult = auditingwithErrorCorrection.verify(challengeData, proof);
                if (verifyResult)
                    System.out.println("Verify pass");
                else
                    System.out.println("Verify failed");
                break;
            }
            default:
                System.out.println("Invalid input");
                break;
        }
    }

    public static void print(byte[] data) {
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[i]));
        }
        System.out.println();
    }

    public static void print(byte[][] data) {
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[0][i]));
        }
        System.out.println();
    }
}
