package com.campus.bazaar.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class LoginFormDTO {
    @NotBlank(message = \"手机号不能为空\")
    @Pattern(regexp = \"^1[3-9]\\\\d{9}$\", message = \"请输入正确的手机号\")
    private String phone;

    @Size(min = 6, max = 6, message = \"验证码为6位\")
    private String code;

    @Size(min = 6, max = 20, message = \"密码长度需要在6-20位之间\")
    private String password;
}
