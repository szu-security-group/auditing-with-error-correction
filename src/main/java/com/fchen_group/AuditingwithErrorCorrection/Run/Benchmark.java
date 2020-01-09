package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.IOException;

import com.javamex.classmexer.MemoryUtil;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Benchmark {

	private long storage = 0;
	//Communication overhead
	private long communication = 0;
	private long time[]; // 0: key generation; 1: outsource; 2: audit; 3: prove;4: verify.
	private String file;
	private int n;
	private int k;
	
	public final static int LOOP_TIMES = 10; // we run the performance
	// evaluation for such times and
	// then average the result.

	public Benchmark(String filepath,int n,int k)
	{
		super();
		this.time = new long[5];
		this.file=filepath;
		this.n=n;
		this.k=k;
	}

	/**
	 * This is the main function to evaluate the performance.
	 * @throws IOException
	 */
	public void run() throws IOException
	{
		randomizedAudit();
	}

	private void randomizedAudit() throws IOException
	{
		//Initializes an object
		AuditingwithErrorCorrection aErrorCorrection = new AuditingwithErrorCorrection(this.file,this.n,this.k);
		
		this.storage = 0;
		this.communication = 0;
		for (int i = 0; i < this.time.length; i++)
			this.time[i] = 0;
		
		long startTime = 0, endTime = 0;
		
		startTime = System.nanoTime();
		aErrorCorrection.generateKey();
		endTime = System.nanoTime();
		this.time[0] = endTime - startTime;
		
		System.out.println("\n-----Outsource-----\n");
		
		startTime = System.nanoTime();
		aErrorCorrection.Outsource();
		endTime = System.nanoTime();
		this.time[1] = endTime - startTime;
		
		//Carry a large integer space
		this.storage = aErrorCorrection.getAllStorage(); 

		System.out.println(".....Auditting.....\n");
		
		Boolean b;
		int count = 0, challengeLen = 460;

		for (int i = 0; i < LOOP_TIMES; i++)
		{
			ChallengeData c;
			startTime = System.nanoTime();
			c = aErrorCorrection.audit(challengeLen);
			endTime = System.nanoTime();
			this.time[2] = this.time[2] + (endTime - startTime);

			ProofData proof;
			startTime = System.nanoTime();
			proof = aErrorCorrection.prove(c);
			endTime = System.nanoTime();
			this.time[3] = this.time[3] + (endTime - startTime);
			//The cost of communication
			this.communication = this.communication + MemoryUtil.deepMemoryUsageOf(proof);

			startTime = System.nanoTime();
			b = aErrorCorrection.verify(c, proof);
			endTime = System.nanoTime();
			this.time[4] = this.time[4] + (endTime - startTime);

			if (b == false)
				count++;


		}
		

		this.time[2] = (long) (this.time[2] / LOOP_TIMES);
		this.time[3] = (long) (this.time[3] / LOOP_TIMES);
		this.time[4] = (long) (this.time[4] / LOOP_TIMES);

		this.communication = (long) (this.communication / LOOP_TIMES);

		System.out.println("\nstorage cost is: " + this.storage + "Bytes");
		System.out.println("communication size is: " + this.communication + "Bytes");
		System.out.println("\ntime is: (ns)");
		for (int i = 0; i < this.time.length; i++)
			System.out.print(this.time[i] + "    ");
		System.out.println("\ncorrespoding to keygen(0), outsource (1), audit(2), prove(3), verify(4)");

		System.out.println("\nverification error: " + count);

	}
	
}
