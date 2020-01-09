package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.IOException;
import java.util.Scanner;

public class Running {
    @SuppressWarnings("resource")
    public static void main(String[] args) throws IOException {
        //Input related parameters
        Scanner scan = new Scanner(System.in);
        String filepath = "C:\\Users\\Administrator\\Desktop\\100MB.rar";

        System.out.print("Please enter the block length of Reed-Solomon codes n: ");
        int n = scan.nextInt();
        System.out.print("Please enter the message length of Reed-Solomon codes k: ");
        int k = scan.nextInt();

        Benchmark b = new Benchmark(filepath, n, k);
        b.run();
    }
}
