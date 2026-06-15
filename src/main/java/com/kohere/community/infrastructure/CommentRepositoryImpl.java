package com.kohere.community.infrastructure;

import com.kohere.community.domain.Comment;
import com.kohere.community.domain.CommentRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 댓글 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link CommentRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로 교체한다. */
@Repository
public class CommentRepositoryImpl implements CommentRepository {

  @Override
  public Optional<Comment> findById(Long commentId) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
