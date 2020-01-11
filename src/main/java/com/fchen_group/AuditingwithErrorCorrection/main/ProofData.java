package com.fchen_group.AuditingwithErrorCorrection.main;

import java.io.Serializable;

public class ProofData implements Serializable {
	private static final long serialVersionUID = 8074523617533993986L;
	public byte[] dataproof;
	public byte[] parityproof;

	public ProofData (byte[] dataproof,byte[] parityproof) {
		this.dataproof=dataproof;
		this.parityproof=parityproof;
	}
}
