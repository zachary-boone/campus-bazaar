package com.campus.bazaar.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class GoodsDTO implements Serializable {
    private Long id;

    @NotBlank(message = \"商品名称不能为空\")
    @Size(max = 128, message = \"商品名称不能超过128个字符\")
    private String name;

    @NotNull(message = \"商品分类不能为空\")
    private Long typeId;

    @NotNull(message = \"商品价格不能为空\")
    @Min(value = 1, message = \"价格不能低于1分\")
    private Long price;

    @Size(max = 64, message = \"校区信息不能超过64个字符\")
    private String area;

    @Size(max = 128, message = \"楼栋信息不能超过128个字符\")
    private String address;

    @Size(max = 1024, message = \"商品图片信息过长\")
    private String images;

    private Integer status;

    private Double x;

    private Double y;

    @Size(max = 64, message = \"交易时间信息过长\")
    private String tradeTime;
}
