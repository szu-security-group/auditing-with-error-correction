package com.fchen_group.AuditingwithErrorCorrection.main;

import java.io.Serializable;

public class Key implements Serializable {
    private static final long serialVersionUID = 8074523633229932986L;
    public String k;
    public String s;

    public Key(String k, String s) {
        this.k = k;
        this.s = s;
    }
}
