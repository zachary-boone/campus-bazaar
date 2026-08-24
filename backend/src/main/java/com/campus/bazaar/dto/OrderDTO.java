package com.campus.bazaar.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class OrderDTO {
    @NotNull(message = \"商品ID不能为空\")
    private Long goodsId;

    @NotNull(message = \"订单金额不能为空\")
    @Min(value = 1, message = \"金额不能低于1分\")
    private Long amount;

    @Size(max = 128, message = \"交易地点不能超过128个字符\")
    private String tradeLocation;

    @Size(max = 64, message = \"交易时间不能超过64个字符\")
    private String tradeTime;

    @Size(max = 256, message = \"备注不能超过256个字符\")
    private String remark;
}
