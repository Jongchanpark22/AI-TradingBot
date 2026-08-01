package com.example.cryptobot.report;

/**
 * LLM 클라이언트 추상화 인터페이스.
 * Gemini, OpenAI 등 다양한 LLM을 동일한 방식으로 호출합니다.
 */
public interface LlmClient {

    /**
     * 프롬프트를 전송하고 텍스트 응답을 받습니다.
     *
     * @param prompt 사용자 프롬프트
     * @return LLM 생성 텍스트
     */
    String generate(String prompt);
}
