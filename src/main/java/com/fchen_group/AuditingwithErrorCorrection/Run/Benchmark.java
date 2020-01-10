package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.IOException;

import com.javamex.classmexer.MemoryUtil;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Benchmark {
    private String file;
    private int n;
    private int k;

    public final static int LOOP_TIMES = 10; // we run the performance
    // evaluation for such times and
    // then average the result.

    public Benchmark(String filepath, int n, int k) {
        super();
        this.file = filepath;
        this.n = n;
        this.k = k;
    }

    /**
     * This is the main function to evaluate the performance.
     */
    public void run() throws IOException {
        randomizedAudit();
    }

    private void randomizedAudit() throws IOException {
        //Initializes an object
        AuditingwithErrorCorrection aErrorCorrection = new AuditingwithErrorCorrection(this.file, this.n, this.k);

        long storage = 0;
        long communication = 0;
        long[] time = new long[5];   // 0: key generation; 1: outsource; 2: audit; 3: prove;4: verify.
        long startTime = 0, endTime = 0;

        startTime = System.nanoTime();
        aErrorCorrection.generateKey();
        endTime = System.nanoTime();
        time[0] = endTime - startTime;

        System.out.println("\n-----Outsource-----\n");

        startTime = System.nanoTime();
        aErrorCorrection.Outsource();
        endTime = System.nanoTime();
        time[1] = endTime - startTime;

        //Carry a large integer space
        storage = aErrorCorrection.getAllStorage();

        System.out.println(".....Auditting.....\n");

        boolean b;
        int count = 0, challengeLen = 460;

        for (int i = 0; i < LOOP_TIMES; i++) {
            ChallengeData c;
            startTime = System.nanoTime();
            c = aErrorCorrection.audit(challengeLen);
            endTime = System.nanoTime();
            time[2] = time[2] + (endTime - startTime);

            ProofData proof;
            startTime = System.nanoTime();
            proof = aErrorCorrection.prove(c);
            endTime = System.nanoTime();
            time[3] = time[3] + (endTime - startTime);
            //The cost of communication
            communication = communication + MemoryUtil.deepMemoryUsageOf(proof);

            startTime = System.nanoTime();
            b = aErrorCorrection.verify(c, proof);
            endTime = System.nanoTime();
            time[4] = time[4] + (endTime - startTime);

            if (!b)
                count++;
        }

        time[2] = (time[2] / LOOP_TIMES);
        time[3] = (time[3] / LOOP_TIMES);
        time[4] = (time[4] / LOOP_TIMES);

        communication = (communication / LOOP_TIMES);

        System.out.println("\nstorage cost is: " + storage + "Bytes");
        System.out.println("communication size is: " + communication + "Bytes");
        System.out.println("\ntime is: (ns)");
        for (long t : time)
            System.out.print(t + "    ");
        System.out.println("\ncorrespoding to keygen(0), outsource (1), audit(2), prove(3), verify(4)");

        System.out.println("\nverification error: " + count);
    }
}
