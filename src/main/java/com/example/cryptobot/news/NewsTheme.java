package com.example.cryptobot.news;

import java.util.List;

/**
 * 뉴스 소분류 테마.
 * 각 테마는 부모 대분류(NewsCategory)와 키워드 목록을 가집니다.
 * 제목+요약에 키워드가 포함되면 해당 테마로 분류됩니다.
 */
public enum NewsTheme {

    // ── 경제 ECONOMY ───────────────────────────────────────────────────────────
    STOCK(NewsCategory.ECONOMY,
            List.of("증시", "코스피", "코스닥", "상장", "종목", "공모주", "배당")),
    REAL_ESTATE(NewsCategory.ECONOMY,
            List.of("부동산", "아파트", "분양", "전세", "집값", "재건축", "종부세")),
    RATE(NewsCategory.ECONOMY,
            List.of("금리", "기준금리", "한은", "한국은행", "환율", "달러", "통화")),
    SEMICONDUCTOR(NewsCategory.ECONOMY,
            List.of("반도체", "HBM", "D램", "파운드리", "삼성전자", "SK하이닉스", "엔비디아")),
    INDUSTRY(NewsCategory.ECONOMY,
            List.of("자동차", "조선", "철강", "중공업", "배터리", "2차전지", "방산")),
    CRYPTO(NewsCategory.ECONOMY,
            List.of("비트코인", "코인", "가상자산", "암호화폐", "이더리움", "스테이블코인")),
    GLOBAL_ECON(NewsCategory.ECONOMY,
            List.of("연준", "FOMC", "관세", "무역", "나스닥", "미국경제")),

    // ── 정치 POLITICS ──────────────────────────────────────────────────────────
    GOVERNMENT(NewsCategory.POLITICS,
            List.of("대통령", "정부", "국무회의", "청와대", "대통령실")),
    PARTY(NewsCategory.POLITICS,
            List.of("국회", "여당", "야당", "국민의힘", "민주당", "의원")),
    DIPLOMACY(NewsCategory.POLITICS,
            List.of("외교", "국방", "북한", "정상회담")),
    ELECTION(NewsCategory.POLITICS,
            List.of("선거", "공천", "후보", "투표", "개표")),
    POLICY(NewsCategory.POLITICS,
            List.of("정책", "법안", "개혁", "규제", "예산")),

    // ── 이슈 ISSUE ─────────────────────────────────────────────────────────────
    INCIDENT(NewsCategory.ISSUE,
            List.of("사고", "화재", "사망", "실종", "붕괴", "수사")),
    LEGAL(NewsCategory.ISSUE,
            List.of("검찰", "법원", "판결", "기소", "구속", "재판")),
    LABOR_WELFARE(NewsCategory.ISSUE,
            List.of("노조", "파업", "임금", "복지", "연금", "고용")),
    EDUCATION(NewsCategory.ISSUE,
            List.of("교육", "대학", "입시", "학교", "수능")),
    ENVIRONMENT(NewsCategory.ISSUE,
            List.of("환경", "기후", "폭염", "태풍", "미세먼지", "탄소")),

    // ── 스포츠 SPORTS ──────────────────────────────────────────────────────────
    BASEBALL(NewsCategory.SPORTS,
            List.of("야구", "KBO", "프로야구", "홈런")),
    SOCCER(NewsCategory.SPORTS,
            List.of("축구", "K리그", "손흥민", "월드컵", "EPL")),
    GOLF(NewsCategory.SPORTS,
            List.of("골프", "KPGA", "KLPGA")),
    BALL(NewsCategory.SPORTS,
            List.of("농구", "배구", "KBL", "V리그")),
    GLOBAL_SPORTS(NewsCategory.SPORTS,
            List.of("올림픽", "MLB", "NBA", "해외리그")),

    // ── 세상이야기 LIFE ────────────────────────────────────────────────────────
    ENTERTAINMENT(NewsCategory.LIFE,
            List.of("연예", "배우", "아이돌", "드라마", "영화", "가수")),
    CULTURE(NewsCategory.LIFE,
            List.of("문화", "전시", "공연", "미술", "축제")),
    TECH_LIFE(NewsCategory.LIFE,
            List.of("IT", "과학", "인공지능", "스마트폰", "우주", "게임")),
    TRAVEL_FOOD(NewsCategory.LIFE,
            List.of("여행", "맛집", "음식", "레저", "캠핑")),
    HEALTH(NewsCategory.LIFE,
            List.of("건강", "의료", "질병", "병원", "다이어트"));

    private final NewsCategory category;
    private final List<String> keywords;

    NewsTheme(NewsCategory category, List<String> keywords) {
        this.category = category;
        this.keywords = keywords;
    }

    public NewsCategory getCategory() {
        return category;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}
