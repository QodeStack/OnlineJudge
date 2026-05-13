package com.autojudge.model;

public class SubmissionRecord {

    private int    id;
    private String timestamp;
    private String language;
    private String code;
    private String problemText;
    private String testCasesJson; // MỚI: Lưu danh sách đề (Input/Expected)
    private String resultsJson;   // MỚI: Lưu danh sách kết quả (Pass/Fail, Time)
    private int    totalTests;
    private int    passedTests;
    private String status;

    public SubmissionRecord(int id, String timestamp, String language, String code,
                            String problemText, String testCasesJson, String resultsJson,
                            int totalTests, int passedTests, String status) {
        this.id            = id;
        this.timestamp     = timestamp;
        this.language      = language;
        this.code          = code;
        this.problemText   = problemText;
        this.testCasesJson = testCasesJson;
        this.resultsJson   = resultsJson;
        this.totalTests    = totalTests;
        this.passedTests   = passedTests;
        this.status        = status;
    }

    public SubmissionRecord(String timestamp, String language, String code, 
                            String problemText, String testCasesJson, String resultsJson,
                            int totalTests, int passedTests, String status) {
        this(-1, timestamp, language, code, problemText, testCasesJson, resultsJson, totalTests, passedTests, status);
    }

    public int    getId()            { return id; }
    public String getTimestamp()     { return timestamp; }
    public String getLanguage()      { return language; }
    public String getCode()          { return code; }
    public String getProblemText()   { return problemText; }
    public String getTestCasesJson() { return testCasesJson; }
    public String getResultsJson()   { return resultsJson; }
    public int    getTotalTests()    { return totalTests; }
    public int    getPassedTests()   { return passedTests; }
    public String getStatus()        { return status; }

    public String getScoreText() {
        return passedTests + " / " + totalTests;
    }
}