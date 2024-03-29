package com.backend.oauthlogin.entity;

import com.backend.oauthlogin.dto.ProfileDeleteDto;
import com.backend.oauthlogin.dto.ProfileUpdateDto;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.*;

import javax.persistence.*;

import static javax.persistence.FetchType.LAZY;

@Entity
@Table(name = "PROFILE")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile extends BaseTimeEntity {

    @Id
    @Column(name = "profile_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long profileId;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "comment", length = 100)
    private String comment;

    @Column(name = "picture")
    private String picture;

    @Column(name = "is_represent")
    private boolean isRepresent;

    @Column(name = "activated")
    private boolean activated;


    public void selectMainProfile() {
        this.isRepresent = true;
        System.out.println("Profile Entity - selectMainProfile() 실행");
    }

    public void cancelMainProfile() {
        this.isRepresent = false;
    }

    public Profile update(ProfileUpdateDto updateDto) {
        this.nickname = updateDto.getNickname();
        this.comment = updateDto.getComment();
        this.picture = updateDto.getPicture();
        this.isRepresent = updateDto.isRepresent();

        return this;
    }

    public Profile delete(Long profileId) {
        this.activated = false;
        return this;
    }

}
