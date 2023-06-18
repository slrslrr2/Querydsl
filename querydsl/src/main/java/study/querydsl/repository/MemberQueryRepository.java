package study.querydsl.repository;

import org.springframework.stereotype.Repository;

@Repository
public class MemberQueryRepository {
    // 딱 특정 화면에 특화된 API의 경우 해당 내용을 적는다.
    // 기본은 MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom
    // 에서 MemberRepositoryCustom을 사용하는게 맞다.
}
