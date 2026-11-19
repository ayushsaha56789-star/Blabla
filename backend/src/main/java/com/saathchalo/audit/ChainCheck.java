package com.saathchalo.audit;

/** Result of re-computing the audit log's hash chain. {@code firstBrokenId} is null when everything is intact. */
public record ChainCheck(boolean intact, long checked, Long firstBrokenId) {
}
