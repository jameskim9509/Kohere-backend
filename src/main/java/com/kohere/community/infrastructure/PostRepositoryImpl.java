package com.kohere.community.infrastructure;

import com.kohere.community.domain.Post;
import com.kohere.community.domain.PostRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 게시글 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link PostRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class PostRepositoryImpl implements PostRepository {

  @Override
  public Optional<Post> findById(Long postId) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
