package com.example.studyroom.domain; // 자바 컴파일러에게 컴퓨터의 물리주소와 ㅣㅂ슷하게,

import jakarta.persistence.*;

import java.time.LocalDateTime;
//Domain = 상태 + 규칙(business rule)을 함께 가진다.
// record 형태가 아님. 상태가 바뀔 수 있어야하니까.

@Entity
public class Reservation{
    private String roomName;
    private String requesterName;
    private boolean confirmed;
    private String cancelReason;

    // Day33 — 같은 방의 예약 시간대 중복을 막으려면 "언제부터 언제까지"가 있어야 한다.
    // V1~V6까지 만들어진 기존 예약(시간대 없음)과 호환되도록 nullable로 둔다 —
    // 그래서 기존 생성자 Reservation(roomName, requesterName)는 이 둘을 null로 남겨둔다.
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // DB에 저장될 때, 자동으로 생성되는 PK값을 담기 위한 필드. final 아님. 저장되기 전에는 아직 값이 없음

    public Reservation(String roomName,String requesterName){
        this.roomName = roomName;
        this.requesterName = requesterName;
        this.confirmed = false; // 아직 확인되면 안되니까 기본값은 false로 둔다.
    }

    // Day33 — 시간대가 있는 예약. 시간대가 없던 기존 생성자를 그대로 재사용해서(this(...))
    // 두 생성자가 "확정 여부 초기값 false"라는 규칙을 중복 없이 공유하게 한다.
    public Reservation(String roomName, String requesterName, LocalDateTime startAt, LocalDateTime endAt){
        this(roomName, requesterName);
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public LocalDateTime getStartAt(){
        return startAt;
    }

    public LocalDateTime getEndAt(){
        return endAt;
    }

    public void assignMember(Member member){
        this.member = member;
    }

    public Member getMember(){
        return member;
    }
    // 상태를 바꾸는 규칙을 객체 안에 캡슐화 - 외부에서 필드를 직접 못 건드리게 하기 위해서.
    public void confirm() {
        this.confirmed = true;
    }

    public void cancel(String cancelReason) { this.confirmed = false; this.cancelReason = cancelReason;}

    public String getRoomName(){
        return roomName;
    }

    public String getRequesterName(){
        return requesterName;
    }

    public boolean isConfirmed(){
        return confirmed;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getId(){
        return id;
    }

    protected Reservation(){

    }

    public String getCancelReason() { return cancelReason; }
}

/*
의문. 왜 package를 굳이 써주지?

답변.
자바 파일이 프로젝트의 수많은 폴더 중 정확히 어디에 속해 있는 '주소(위치)'인지 알려주기 위해서"입니다.

컴퓨터의 폴더 경로와 똑같다고 생각하시면 아주 쉽습니다!

1. 현실 세계의 비유: '김철수'라는 사람 찾기
만약 대한민국에 '김철수'라는 사람이 1,000명 있다면, 그 사람을 정확히 찾기 위해 전체 주소를 붙여야 합니다.

서울특별시.강남구.역삼동.김철수

경기도.수원시.팔달구.김철수

자바에서도 똑같이 Reservation이라는 클래스 이름이 다른 라이브러리나 다른 폴더에 중복으로 존재할 수 있습니다. 그래서 com.example.studyroom.domain.Reservation처럼 전체 주소(패키지 + 클래스명)를 부여해서 이름이 충돌하는 것을 막아주는 것입니다.

2. 패키지 선언이 해주는 핵심 역할 2가지
① 파일 구조 및 물리적 폴더 매핑
package com.example.studyroom.domain; 이라고 선언해 두면, 자바 컴파일러는 이 파일이 실제 컴퓨터의 src/main/java/com/example/studyroom/domain/ 폴더 경로에 들어있다고 인식합니다.

② 다른 곳에서 가져다 쓸 때(import) 필요
나중에 컨트롤러나 서비스 같은 다른 폴더에서 이 Reservation 클래스를 가져다 쓰고 싶을 때, 자바에게 어디서 가져올지 알려주려면 패키지 주소가 필수적입니다.


package com.example.studyroom.controller;

// domain 패키지에 있는 Reservation 클래스를 가져와서 쓰겠다!
import com.example.studyroom.domain.Reservation;

public class ReservationController {
    private Reservation reservation;
}
💡 한 줄 요약
**package는 자바 파일이 거주하는 '도로명 주소'**입니다.
파일 이름이 똑같은 다른 클래스와 헷갈리지 않게 관리하고, 다른 파일에서 import로 쉽게 찾아올 수 있도록 맨 위에 적어두는 것입니다!
 */