package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryFactory;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.persistence.Query;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
@Rollback(value = false)
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        // 1. Member1찾아라
        String username = "member";
        String qlString = "select m from Member m where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", username)
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo(username);
    }

    @Test
    public void startQuerydsl() {
        // 1. Member1찾아라
        String username = "member";
        Member findMember = queryFactory.select(member) // QueryDSL은 컴파일 시점에 잘못된 쿼리를 잡아준다., 파라미터바인딩을 해준다.
                .from(member)
                .where(member.username.eq(username))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo(username);
    }

    @Test
    public void search() {
        String username = "member";
        Member findMember = queryFactory
                .selectFrom(QMember.member)
                .where(QMember.member.username.eq(username)
                        .and(QMember.member.age.between(10, 30)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo(username);

        int findMemberAge = findMember.getAge();
        assertTrue(10 >= findMemberAge && findMemberAge < 30);
    }

    @Test
    public void searchAnd() {
        String username = "member";
        Member findMember = queryFactory
                .selectFrom(QMember.member)
                .where(
                        QMember.member.username.eq(username)
                        , (QMember.member.age.between(10, 30))
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo(username);

        int findMemberAge = findMember.getAge();
        assertTrue(10 >= findMemberAge && findMemberAge < 30);
    }

    @Test
    public void resultFetch() {
        List<Member> fetch = queryFactory.selectFrom(member).fetch();
//        Member fetchOne = queryFactory.selectFrom(member).fetchOne(); // 단 건 조회
        // , 둘이상아면 NonUniqueResultException

        Member fetchFirst = queryFactory.selectFrom(member).fetchFirst(); // limit(1).fetchOne()

        // 아래 내용은 복잡한 쿼리 사용 시 실제 쿼리가 다르게 표시될 수 있음
        QueryResults<Member> results = queryFactory.selectFrom(member).fetchResults(); // 페이징 정보 포함, total count 쿼리 추가 실행
        results.getTotal(); // total count 쿼리 추가 실행
        List<Member> members = results.getResults();

        long l = queryFactory.selectFrom(member).fetchCount(); //count 쿼리로 변경
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> results = queryFactory.selectFrom(member)
                .where(member.age.goe(100)) // age <= 100
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) //null을 가장 마지막으로 한다.
                .fetch();

        assertThat(results.get(0).getUsername()).isEqualTo("member5");
        assertThat(results.get(1).getUsername()).isEqualTo("member6");
        assertThat(results.get(2).getUsername()).isNull();
    }

    @Test
    public void paging1() {
        // 전체 데이터
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc()).offset(1) //0부터 시작(zero index)
                .limit(2) //최대 2건 조회
                .fetch();

        // 전체 카운트 + 데이터
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc()).offset(1) //0부터 시작(zero index)
                .limit(2) //최대 2건 조회
                .fetchResults();

        long total = results.getTotal();// 카운트
        List<Member> members = results.getResults();

        assertThat(result.size()).isEqualTo(2);
    }

    /**
     * JPQL
     * select
     * COUNT(m), //회원수
     * SUM(m.age), //나이 합
     * AVG(m.age), //평균 나이
     * MAX(m.age), //최대 나이
     * MIN(m.age) //최소 나이 * from Member m
     */
    @Test
    public void aggregation() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min())
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    @Test
    void group() throws Exception {
        // given
        List<Tuple> result = queryFactory.select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        // when
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        // then
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10, 20) / 2
        // then
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30, 40) / 2
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() {
        List<Member> teamA = queryFactory.select(member)
                .from(member)
                .leftJoin(member.team, team)
                //.join(member.team, team)
                .where(member.team.name.eq("teamA"))
                .fetch();

        assertThat(teamA).extracting("username")
                .containsExactly("member", "member2");
    }

    /**
     * 세다조인 : 연관관계가 없는 Join 해보기
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> members = queryFactory.select(member)
                .from(member, team) // from 절에 선택해서 세타조인
                .where(member.username.eq(team.name)) // left, outer join이 불가능하다 이는 아래에서 가능한 경우가 있다
                .fetch();
        /**
         select
         member0_.member_id as member_i1_1_, member0_.age as age2_1_, member0_.team_id as team_id4_1_, member0_.username as username3_1_
         from member member0_
         [cross join team team1_] **
         where member0_.username=team1_.name
         **/

        assertThat(members).extracting("username")
                .containsExactly("teamA", "teamB");
    }

    @Test
    public void join_leftJoin_on() {
        queryFactory.select(member, team).from(member)
                .leftJoin(team).on(member.team.eq(team)).fetch();

        queryFactory.select(member, team).from(member)
                .leftJoin(member.team, team).on(member.team.eq(team)).fetch();

        // left outer join team team1_on ( member0_.team_id=team1_.team_id )
        // left outer join team team1_ on member0_.team_id=team1_.team_id and ( member0_.team_id=team1_.team_id )

    }

    // 1. 조인 대상 필터링

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t (ON m.TEAM_ID=t.id and t.name='teamA')
     * 해당 ON절에 t.name='teamA' 조건을 주게되면
     * member는 모두 나오는 상태에서 teamA left outer join 하게된다.
     * => team이 NULL로 나올 수 있음
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> tuples = queryFactory.select(member, team).from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA")).fetch();

        // from member member0_ left outer join team team1_ on member0_.team_id=team1_.team_id and (team1_.name=teamA);
        for (Tuple tuple : tuples) {
            System.out.println(tuple);
        }

        /**
         [Member(id=3, username=member, age=10), Team(id=1, name=teamA)]
         [Member(id=4, username=member2, age=20), Team(id=1, name=teamA)]
         [Member(id=5, username=member3, age=30), null]
         [Member(id=6, username=member4, age=40), null]
         */
    }

    // 2. 연관관계 없는 엔티티 외부 조인 ** [실무에서 은근 사용]

    /**
     * 세다조인 : 연관관계가 없는 Join 해보기
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void join_on_no_relation() {
        em.persist(new Team("teamC"));
        em.persist(new Team("teamD"));

        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        getAllMembers();
        /**
         [Member(id=3, username=member, age=10), Team(id=1, name=teamA)]
         [Member(id=4, username=member2, age=20), Team(id=1, name=teamA)]
         [Member(id=5, username=member3, age=30), Team(id=2, name=teamB)]
         [Member(id=6, username=member4, age=40), Team(id=2, name=teamB)]
         [Member(id=9, username=teamA, age=0), null]
         [Member(id=10, username=teamB, age=0), null]
         [Member(id=11, username=teamC, age=0), null]
         */
        getAllTeams();
        /**
         Team(id=1, name=teamA)
         Team(id=2, name=teamB)
         Team(id=7, name=teamC)
         Team(id=8, name=teamD)
         */

        List<Tuple> fetch = queryFactory
                .select(member, team)
                .from(member)                       // from member member0_
                .leftJoin(team)                     // left outer join team team1_
                .on(member.username.eq(team.name))  // on (member0_.username=team1_.name);
                .fetch();

        for (Tuple tuple : fetch) {
            System.out.println(tuple);
        }
        /**
         [Member(id=3, username=member, age=10), null]
         [Member(id=4, username=member2, age=20), null]
         [Member(id=5, username=member3, age=30), null]
         [Member(id=6, username=member4, age=40), null]
         [Member(id=7, username=teamA, age=0), Team(id=1, name=teamA)]
         [Member(id=8, username=teamB, age=0), Team(id=2, name=teamB)]
         [Member(id=9, username=teamC, age=0), null]
         */

    }

    private void getAllTeams() {
        List<Team> teams = queryFactory.selectFrom(team).fetch();
        for (Team entity : teams) {
            System.out.println(entity);
        }
    }

    private void getAllMembers() {
        List<Tuple> fetch = queryFactory.select(member, team).from(member).leftJoin(member.team, team).fetch();
        for (Tuple tuple : fetch) {
            System.out.println(tuple);
        }
    }

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        // select member0_.member_id as member_i1_1_, member0_.age as age2_1_, member0_.team_id as team_id4_1_, member0_.username as username3_1_
        // from member member0_ where member0_.username=1 limit 1;
        // LAZY로 하였기에 딱 Member만 SELECT, FROM 한다.

        // 만약 LAZY 를 Member에서 지울 경우 N+1 번 나간다
        // from member member0_ where member0_.username=? limit ? // 1
        // from team team0_ where team0_.team_id=?  // N
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member"))
                .fetchFirst();

    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo2() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member"))
                .fetchFirst();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoin() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .fetchJoin()
                .where(member.username.eq("member"))
                .fetchFirst();
        // 패치조인 특징 1. member, team 모든 컬럼 가져온다
        // 한방쿼리로 가져온다
        // select member0_.member_id as member_i1_1_0_, team1_.team_id as team_id1_2_1_, member0_.age as age2_1_0_, member0_.team_id as team_id4_1_0_, member0_.username as username3_1_0_, team1_.name as name2_2_1_
        // from member member0_
        // inner join team team1_
        // on member0_.team_id=team1_.team_id where member0_.username=1;

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQueryByWhere() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균보다 큰 회원
     */
    @Test
    public void subQuery2ByWhere() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    @Test
    public void subQueryInByWhere() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    public void subQueryBySelect() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> fetch = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg()) // SELECT에 평균값 보여주기
                                .from(memberSub)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : fetch) {
            System.out.println(tuple);
        }
        /**
         [member, 25.0]
         [member2, 25.0]
         [member3, 25.0]
         [member4, 25.0]
         */
    }

    @Test
    public void basicCase() {
        List<String> fetch = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                ).from(member)
                .fetch();
        for (String s : fetch) {
            System.out.println(s);
        }
    }

    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살").when(member.age.between(21, 30)).then("21~30살").otherwise("기타"))
                .from(member)
                .fetch();
    }

    @Test
    public void constant() {
        queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

    }

    @Test
    public void concat() {
        List<String> fetch = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member"))
                .fetch();
        for (String s : fetch) {
            System.out.println(s);
        }
    }

    /**
     * 중국문법
     **/
    @Test
    public void simpleProjection() {
        List<String> fetch = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : fetch) {
            System.out.println(s);
        }
    }

    @Test
    public void tupleProjection() {
        List<Tuple> fetch = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : fetch) {
            System.out.println(tuple.get(member.username));
            System.out.println(tuple.get(member.age));
            System.out.println(tuple);
        }
    }

    @Test
    public void findDtoByJPQL() {
        /**
         순수 JPA에서 DTO를 조회할 때는 new 명령어를 사용해야함
         DTO의 package이름을 다 적어줘야해서 지저분함
         생성자 방식만 지원함
         */
        List<Member> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", Member.class)
                .getResultList();

        for (Member member1 : resultList) {
            System.out.println(member1);
        }
    }

    @Test
    @DisplayName("DTO 조회 - 1.프로퍼티 접근 - Setter(Projections.bean)")
    public void findDtoBySetter_fit() {
        List<MemberDto> fetch = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : fetch) {
            System.out.println(memberDto);
        }
    }

    @Test
    @DisplayName("DTO 조회 - 2. 필드 직접 접근(Projections.fields)")
    public void findDtoByFields_fit() {
        List<MemberDto> fetch = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : fetch) {
            System.out.println(memberDto);
        }
    }

    @Test
    @DisplayName("DTO 조회 - 3. 필드 직접 접근(Projections.constructor)")
    public void findDtoByConstructor_fit() {
        List<MemberDto> fetch = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : fetch) {
            System.out.println(memberDto);
        }
    }

    @Test
    @DisplayName("DTO 조회 - 컬럼명(username)과 다른 변수명에 값을 넣고싶을 경우 UserDto(user)")
    public void findUserDtoByFields_no_fit() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> fetch = queryFactory
                .select(Projections.fields(UserDto.class, //fields의 경우 필드명을 가지고 주입하기에
                        member.username.as("name"), // AS 를 사용하여 UserDto에 넣고싶은 변수명과 맞춰준다
                        ExpressionUtils.as(
                                JPAExpressions          // 서브쿼리의 경우 JPAExpressions를 사용하여 별칭을 준다
                                        .select(memberSub.age.max())
                                        .from(memberSub), "age"
                        )
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : fetch) {
            System.out.println(userDto);
        }
        /**
         UserDto(name=member, age=10)
         UserDto(name=member2, age=20)
         UserDto(name=member3, age=30)
         UserDto(name=member4, age=40)
         */
    }

    @Test
    @DisplayName("DTO 조회 - 5. 필드 직접 접근(Projections.constructor)" +
            "컬럼명(username)과 다른 변수명에 값을 넣고싶을 경우 UserDto(user)")
    public void findUserDtoByConstructor_no_fit() {
        List<UserDto> fetch = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username, // constructor이기에 변수명은 신경안쓰고,
                        member.age))     // 컬럼 순서를 신경써줘야한다.
                .from(member)
                .fetch();

        for (UserDto userDto : fetch) {
            System.out.println(userDto);
        }
    }

    @Test
    @DisplayName("@QueryProjection 사용하기" +
            "장점 : 타입도 정확하게 맞출 수 있는 장점" +
            "단점 : Repository, Controller, Service등 DTO가 모두 쓰이기에 소스가 지저분하여 김영한님은 [Projections.constructor] 를 사용한다하심")
    public void findDtoByQueryProjection() {
        List<MemberDto> fetch = queryFactory
                .select(new QMemberDto(member.username, member.age)) // 타입도 정확하게 맞출 수 있는 장점
                .from(member)                                        // Projections.constructor는 Runtime에 맞출 수 있는데
                .fetch();                                            // 위는 컴파일에서 미리 확인 가능하다

        for (MemberDto memberDto : fetch) {
            System.out.println(memberDto);
        }
    }

    @Test
    @DisplayName("동적쿼리 - BooleanBuilder ")
    public void dynamicQuery_BooleanBuilder() {
        String username = "member";
        Integer age = 10;

        List<Member> result = searchMember(username, age);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember(String username, Integer age) {
        BooleanBuilder builder = new BooleanBuilder();
        if (username != null) builder.and(member.username.eq(username));
        if (age != null) builder.and(member.age.eq(age));

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    @DisplayName("동적쿼리2 - Where 다중 파라미터 사용(선호 - 소스가 깔끔해짐)")
    public void dynamicQuery_WhereParam() {
        String username = "member";
        Integer age = 10;

        List<Member> result = searchMember2(username, age);
        assertThat(result.size()).isEqualTo(1);
    }

    // 장점1 : 메인메소드가 먼저 한눈에 보임
    private List<Member> searchMember2(String username, Integer age) {
        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(username), ageEq(age))
                .where(allEq(username, age))
                .fetch();
    }

    // 장점2 : 메서드가 빠짐으로써 공통화 가능
    private BooleanExpression usernameEq(String username) {
        return (username != null) ? member.username.eq(username) : null;
    }

    private BooleanExpression ageEq(Integer age) {
        return (age != null) ? member.age.eq(age) : null;
    }

    // 장점3 : 조립도 가능
    private BooleanExpression allEq(String username, Integer age) {
        return usernameEq(username).and(ageEq(age));
    }

    @Test
    @Commit
    @DisplayName("Bulk update")
    public void bulkUpdate() {
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        long count2 = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();


        long count3 = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

        // 주의할점 : 업데이트 이후, 영속성컨텍스트에 반영이 안된다!!
        em.flush();
        em.clear();
    }

    @Test
    public void sqlFunction() {
        List<String> fetch = queryFactory
                .select(Expressions.stringTemplate(
                        "function('regexp_replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : fetch) {
            System.out.println(s);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> fetch = queryFactory
//                .select(Expressions.stringTemplate( "function('lower', {0})",member.username))
                .select(member.username.lower()) // 이런 간단한 기능들은 ANSI 표준에 미리 등록되어있다
                .from(member)
                .fetch();

        for (String s : fetch) {
            System.out.println(s);
        }
    }
}
