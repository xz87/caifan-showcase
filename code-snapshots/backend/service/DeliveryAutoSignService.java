package com.caifan.admin.service;

import com.caifan.business.service.DlvOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 已配送超过配置时长且客户仍未主动签收的订单，由系统自动签收。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.delivery.auto-sign-enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryAutoSignService {

    private final DlvOrderService dlvOrderService;

    @Value("${app.delivery.auto-sign-hours:12}")
    private int autoSignHours;

    @Scheduled(
            fixedDelayString = "${app.delivery.auto-sign-interval-ms:300000}",
            initialDelayString = "${app.delivery.auto-sign-initial-delay-ms:30000}"
    )
    public void autoSignOverdueDeliveries() {
        dlvOrderService.autoSignOverdue(LocalDateTime.now(), autoSignHours);
    }
}
