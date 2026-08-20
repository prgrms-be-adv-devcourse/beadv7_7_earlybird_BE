package com.growmighty.lectures.firstday.user.domain;

/** 토스페이먼츠 지급대행 은행 코드(두 자리) — https://docs.tosspayments.com/codes/org-codes */
public enum BankCode {
    KYONGNAMBANK("39", "경남은행"),
    GWANGJUBANK("34", "광주은행"),
    LOCALNONGHYEOP("12", "단위농협(지역농축협)"),
    BUSANBANK("32", "부산은행"),
    SAEMAUL("45", "새마을금고"),
    SANLIM("64", "산림조합"),
    SHINHAN("88", "신한은행"),
    SHINHYEOP("48", "신협"),
    CITI("27", "씨티은행"),
    WOORI("20", "우리은행"),
    POST("71", "우체국예금보험"),
    SAVINGBANK("50", "저축은행중앙회"),
    JEONBUKBANK("37", "전북은행"),
    JEJUBANK("35", "제주은행"),
    KAKAOBANK("90", "카카오뱅크"),
    KBANK("89", "케이뱅크"),
    TOSSBANK("92", "토스뱅크"),
    HANA("81", "하나은행"),
    HSBC("54", "홍콩상하이은행"),
    BOA("60", "Bank of America"),
    IBK("03", "IBK기업은행"),
    KOOKMIN("06", "KB국민은행"),
    DAEGUBANK("31", "iM뱅크(대구)"),
    KDBBANK("02", "한국산업은행"),
    NONGHYEOP("11", "NH농협은행"),
    SC("23", "SC제일은행"),
    SUHYEOP("07", "Sh수협은행"),
    SUHYEOPLOCALBANK("30", "수협중앙회");

    private final String code;
    private final String bankName;

    BankCode(String code, String bankName) {
        this.code = code;
        this.bankName = bankName;
    }

    public String getCode() {
        return code;
    }

    public String getBankName() {
        return bankName;
    }

    public static BankCode fromCode(String code) {
        for (BankCode bankCode : values()) {
            if (bankCode.code.equals(code)) {
                return bankCode;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 은행 코드입니다. bankCode=" + code);
    }
}
