package study.querydsl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class UserDto {
    private String name; // 변수명은 username 인데,
                         // name으로 설정하여 테스트 코드 Select DTO 어떻게 만드나 활용됨
    private int age;
}
