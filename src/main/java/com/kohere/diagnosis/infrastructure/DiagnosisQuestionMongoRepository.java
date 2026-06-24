package com.kohere.diagnosis.infrastructure;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** 진단 문항 카탈로그 Spring Data MongoDB 리포지토리(infrastructure 내부). */
interface DiagnosisQuestionMongoRepository
    extends MongoRepository<DiagnosisQuestionDocument, String> {

  Optional<DiagnosisQuestionDocument> findFirstByFieldAndActiveTrue(String field);
}
