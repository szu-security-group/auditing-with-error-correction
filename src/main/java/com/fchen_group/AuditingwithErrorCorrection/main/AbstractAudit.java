package com.fchen_group.AuditingwithErrorCorrection.main;

public abstract class AbstractAudit {
    public abstract Key keyGen();
    public abstract byte[][] outsource(Key key);
    public abstract ChallengeData audit(Key key);
    public abstract ProofData prove(byte[][] paritys, ChallengeData challengeData);
    public abstract boolean verify(Key key, ChallengeData challengeData, ProofData proofData);
}
