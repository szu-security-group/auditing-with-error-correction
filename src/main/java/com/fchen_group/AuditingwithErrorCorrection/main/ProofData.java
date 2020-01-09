package com.fchen_group.AuditingwithErrorCorrection.main;

public class ProofData {

	byte[] dataproof;
	byte[] parityproof;
	
	public ProofData (byte[] dataproof,byte[] parityproof) {
		this.dataproof=dataproof;
		this.parityproof=parityproof;
	}
	
}
