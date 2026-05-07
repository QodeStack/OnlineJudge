package com.autojudge.model;

public class TestCase {
    private int id;
    private String input;
    private String expectedOutput;
    private String explanation; // Giải thích ngắn gọn (tùy chọn)

    public TestCase(int id, String input, String expectedOutput, String explanation) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.explanation = explanation;
    }

    // Getters and Setters
    public int getId() { return id; }
    public String getInput() { return input; }
    public String getExpectedOutput() { return expectedOutput; }
    public String getExplanation() { return explanation; }
}