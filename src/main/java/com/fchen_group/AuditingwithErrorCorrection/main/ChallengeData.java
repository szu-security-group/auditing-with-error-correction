package com.fchen_group.AuditingwithErrorCorrection.main;

public class ChallengeData {
    public byte[] coefficients;
    public int[] index;

    public ChallengeData(int[] index, byte[] coefficients) {
        this.index = index;
        this.coefficients = coefficients;
    }
}
