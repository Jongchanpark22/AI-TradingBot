package com.example.cryptobot.news;

/**
 * 뉴스 대분류 카테고리.
 */
public enum NewsCategory {

    ECONOMY("경제"),
    POLITICS("정치"),
    ISSUE("이슈"),
    SPORTS("스포츠"),
    LIFE("세상이야기");

    private final String label;

    NewsCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
