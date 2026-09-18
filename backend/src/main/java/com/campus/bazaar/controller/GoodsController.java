package com.campus.bazaar.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.bazaar.dto.GoodsDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.service.IGoodsLikeService;
import com.campus.bazaar.service.IGoodsService;
import com.campus.bazaar.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Resource
    public IGoodsService goodsService;

    @Resource
    private IGoodsLikeService goodsLikeService;

    /**
     * 根据id查询商品详情
     */
    @GetMapping("/{id}")
    public Result queryGoodsById(@PathVariable("id") Long id) {
        return goodsService.queryById(id);
    }

    /**
     * 商品秒杀（Redis 预扣库存 + MQ 异步建单；双层令牌桶限流保护）
     * @param id 商品id
     * @return 0 表示已受理，订单由消费者异步创建（不是下单成功）
     */
    @PostMapping("/seckill/{id}")
    @com.campus.bazaar.utils.RateLimit(key = "goods-seckill", rate = 20, capacity = 50,
            globalRate = 200, globalCapacity = 500,
            message = "秒杀请求过于频繁，请稍后再试")
    public Result seckillGoods(@PathVariable("id") Long id) {
        return goodsService.seckillGoods(id);
    }

    /**
     * 发布商品
     */
    @PostMapping
    public Result saveGoods(@Valid @RequestBody GoodsDTO goodsDTO) {
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        return goodsService.publishGoods(goods);
    }

    /**
     * 编辑商品
     */
    @PutMapping
    public Result updateGoods(@Valid @RequestBody GoodsDTO goodsDTO) {
        if (goodsDTO.getId() == null) {
            return Result.fail("商品ID不能为空");
        }
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        return goodsService.updateGoods(goods);
    }

    /**
     * 下架商品
     */
    @PutMapping("/off/{id}")
    public Result offShelfGoods(@PathVariable("id") Long id) {
        return goodsService.offShelfGoods(id);
    }

    /**
     * 上架商品
     */
    @PutMapping("/on/{id}")
    public Result onShelfGoods(@PathVariable("id") Long id) {
        return goodsService.onShelfGoods(id);
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Result deleteGoods(@PathVariable("id") Long id) {
        return goodsService.deleteGoods(id);
    }

    /**
     * 根据商品类型分页查询（支持排序）
     * <p>
     * typeId <b>可以不传</b>（或传 0）：表示不限分类，返回全部在售商品。
     * 下面用 {@code eq(条件, ...)} 做了"只在 typeId > 0 时才加分类条件"，
     * 所以这里必须是 required = false —— 之前写成必填，前端不传就直接 500。
     * <p>
     * sortBy 取值（白名单，非法值按"综合"处理，列名不接受外部拼接）：
     * <ul>
     *   <li>空 / 其它 → 综合：按已售 + 人气排序</li>
     *   <li>{@code new}        → 最新发布</li>
     *   <li>{@code priceAsc}   → 价格从低到高</li>
     *   <li>{@code priceDesc}  → 价格从高到低</li>
     *   <li>{@code comments}   → 人气（评论数）</li>
     *   <li>{@code score}      → 评分</li>
     *   <li>{@code wants}      → 求购榜（想要人数，tb_goods.wants 计数列）</li>
     * </ul>
     */
    @GetMapping("/of/type")
    public Result queryGoodsByType(
            @RequestParam(value = "typeId", required = false) Integer typeId,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        QueryWrapper<Goods> wrapper = new QueryWrapper<Goods>()
                .eq(typeId != null && typeId > 0, "type_id", typeId)
                .eq("status", 1);

        // 排序白名单：只在这里映射列名，避免把前端字符串拼进 SQL
        String sort = sortBy == null ? "" : sortBy.trim();
        switch (sort) {
            case "new":
                wrapper.orderByDesc("create_time");
                break;
            case "priceAsc":
                wrapper.orderByAsc("price");
                break;
            case "priceDesc":
                wrapper.orderByDesc("price");
                break;
            case "comments":
                wrapper.orderByDesc("comments");
                break;
            case "score":
                wrapper.orderByDesc("score");
                break;
            case "wants":
                // 求购榜：想要人数多的排前面（wants 与 tb_goods_like 明细同事务维护）
                wrapper.orderByDesc("wants");
                break;
            default:
                // 综合：先按热度，再按人气；最后用 id 兜底保证分页稳定
                wrapper.orderByDesc("sold").orderByDesc("comments");
                break;
        }
        wrapper.orderByDesc("id");

        Page<Goods> result = goodsService.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 想要/取消想要（需登录；幂等；事务内同步维护 tb_goods.wants 计数）
     * @param isLike true=想要 false=取消
     * @return 操作后的最新想要人数
     */
    @PutMapping("/like/{id}/{isLike}")
    public Result like(@PathVariable("id") Long id, @PathVariable("isLike") Boolean isLike) {
        return goodsLikeService.like(id, isLike);
    }

    /**
     * 某商品的想要状态与人数（公开：未登录 liked=false）
     * @return { liked: Boolean, wants: Integer }
     */
    @GetMapping("/like/{id}")
    public Result likeStatus(@PathVariable("id") Long id) {
        return Result.ok(goodsLikeService.status(id));
    }

    /**
     * 根据商品名称关键字分页查询
     */
    @GetMapping("/of/name")
    public Result queryGoodsByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Goods> page = goodsService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .eq("status", 1)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 查询我发布的商品
     */
    @GetMapping("/of/me")
    public Result queryMyGoods(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return goodsService.queryMyGoods(current);
    }

    /**
     * 某卖家正在出售的商品（他人主页用）
     * @param sellerId 卖家用户id
     * @param current  页码
     */
    @GetMapping("/of/seller/{id}")
    public Result queryGoodsOfSeller(@PathVariable("id") Long sellerId,
                                     @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return goodsService.queryGoodsOfSeller(sellerId, current);
    }

    /**
     * 附近商品（Redis GEO 半径检索，按距离升序）
     * @param x      中心经度
     * @param y      中心纬度
     * @param radius 半径（公里，默认 3）
     * @param area   校区（可空，空则全校区）
     */
    @GetMapping("/of/nearby")
    public Result queryNearbyGoods(@RequestParam("x") Double x,
                                   @RequestParam("y") Double y,
                                   @RequestParam(value = "radius", defaultValue = "3") Double radius,
                                   @RequestParam(value = "area", required = false) String area) {
        return goodsService.queryNearbyGoods(x, y, radius, area);
    }

    /**
     * 地图页数据（Redis GEO）：中心点 + 半径内商品 + 各校区在售数量
     * <p>
     * 不传 x/y 就用校园中心（在售商品坐标中位数），所以前端"校园视角"与"我的位置"共用一个接口。
     * @param x      我的经度（可空）
     * @param y      我的纬度（可空）
     * @param radius 半径（公里，默认 3）
     * @param area   校区（可空）
     */
    @GetMapping("/map")
    public Result queryMapGoods(@RequestParam(value = "x", required = false) Double x,
                                @RequestParam(value = "y", required = false) Double y,
                                @RequestParam(value = "radius", required = false) Double radius,
                                @RequestParam(value = "area", required = false) String area) {
        return goodsService.queryMapGoods(x, y, radius, area);
    }

    /**
     * 手动重建 Redis GEO 索引（数据是 SQL 导入时，索引不会自动生成）
     * @return 写入索引的商品数
     */
    @PostMapping("/geo/rebuild")
    public Result rebuildGeoIndex() {
        return Result.ok(goodsService.rebuildGeoIndex());
    }
}
