CREATE TABLE reservation (

     id BIGINT NOT NULL AUTO_INCREMENT,

     room_name VARCHAR(100) NOT NULL,

     requester_name VARCHAR(50) NOT NULL, -- Java는 CamelCase 이름을 사용하지만 SQL은 snake case로, 어차피 Spring Boot의 Hibernate 기본설정이 자동변환해줌.

     confirmed BOOLEAN NOT NULL DEFAULT FALSE,

     PRIMARY KEY (id)

);