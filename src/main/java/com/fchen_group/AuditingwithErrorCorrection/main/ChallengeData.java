package com.fchen_group.AuditingwithErrorCorrection.main;

import java.io.Serializable;

public class ChallengeData implements Serializable {
    private static final long serialVersionUID = 8074523611235693986L;
    public byte[] coefficients;
    public int[] index;

    public ChallengeData(int[] index, byte[] coefficients) {
        this.index = index;
        this.coefficients = coefficients;
    }
}
