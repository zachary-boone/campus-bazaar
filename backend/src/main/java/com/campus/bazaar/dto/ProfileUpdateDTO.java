package com.campus.bazaar.dto;

import lombok.Data;

@Data
public class ProfileUpdateDTO {
    private String nickName;
    private String icon;
    private String introduce;
    private Integer gender;
    private String city;
}
