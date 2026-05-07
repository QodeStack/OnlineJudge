package com.autojudge.model;

public class JudgeResult {
    private int testCaseId;
    private String status; // "AC", "WA", "TLE", "RE" (Runtime Error), "CE"
    private long executionTimeMs;
    private String userOutput;

    public JudgeResult(int testCaseId, String status, long executionTimeMs, String userOutput) {
        this.testCaseId = testCaseId;
        this.status = status;
        this.executionTimeMs = executionTimeMs;
        this.userOutput = userOutput;
    }

    // Getters
    public int getTestCaseId() { return testCaseId; }
    public String getStatus() { return status; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getUserOutput() { return userOutput; }
}