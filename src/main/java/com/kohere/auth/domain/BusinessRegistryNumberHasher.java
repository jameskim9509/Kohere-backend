package com.kohere.auth.domain;

/**
 * 사업자등록번호 단방향 해시 포트(SHA-256 + pepper). 사업자번호 원문은 저장·로그하지 않고 해시만 보관하며(응답은 마스킹), 검증 마커·영속 모두 해시로만
 * 다룬다(ADR-0033 · ADR-0015).
 */
public interface BusinessRegistryNumberHasher {

  String hash(String businessRegistrationNumber);
}
