package com.caifan.business.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caifan.business.entity.LossClaim;
import com.caifan.business.entity.SalOrder;
import com.caifan.business.entity.SalOrderItem;
import com.caifan.business.dto.ReshipForm;
import com.caifan.business.service.LossClaimService;
import com.caifan.common.context.UserContext;
import com.caifan.common.page.PageQuery;
import com.caifan.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/**
 * 商家端损耗报备审核与补发。
 */
@RestController
@RequestMapping("/api/claim")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BOSS')")
public class ClaimController {

    private final LossClaimService claimService;

    @GetMapping("/settings")
    public Result<Map<String, Integer>> settings() {
        Long merchantId = com.caifan.business.support.TenantSupport.merchantId();
        return Result.ok(Map.of("claimWindowHours", claimService.claimWindowHours(merchantId)));
    }

    @PutMapping("/settings")
    public Result<Map<String, Integer>> saveSettings(@RequestBody Map<String, Object> body) {
        Long merchantId = com.caifan.business.support.TenantSupport.merchantId();
        int hours = Integer.parseInt(String.valueOf(body.get("claimWindowHours")));
        int saved = claimService.saveClaimWindowHours(merchantId, hours);
        return Result.ok(Map.of("claimWindowHours", saved), "报损时限已保存");
    }

    @GetMapping("/page")
    public Result<Page<LossClaim>> page(PageQuery query) {
        return Result.ok(claimService.page(query));
    }

    @GetMapping("/{id}/items")
    public Result<List<SalOrderItem>> items(@PathVariable Long id) {
        return Result.ok(claimService.originItems(id));
    }

    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        claimService.audit(id, pass, remark, UserContext.getRealName());
        return Result.ok();
    }

    @PostMapping("/{id}/reship")
    public Result<SalOrder> reship(@PathVariable Long id,
                                   @RequestBody(required = false) ReshipForm form) {
        return Result.ok(claimService.reship(id, form, UserContext.getRealName()), "补发单已创建");
    }
}
