package study.querydsl.repository;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;

import javax.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MemberJpaRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @Test
    public void basicTest() {
        Member member1 = new Member("member1", 10);
        memberJpaRepository.save(member1);

        Member findMember = memberJpaRepository.findById(member1.getId()).get();
        assertThat(member1).isEqualTo(findMember);

        List<Member> result1 = memberJpaRepository.findAll();
        assertThat(result1).containsExactly(member1);

        List<Member> result2 = memberJpaRepository.findAll_Querydsl();
        assertThat(result2).containsExactly(member1);

        List<Member> result3 = memberJpaRepository.findByUsername("member1");
        assertThat(result3).containsExactly(member1);

        List<Member> result4 = memberJpaRepository.findByUsername_Querydsl("member1");
        assertThat(result4).containsExactly(member1);
    }
}