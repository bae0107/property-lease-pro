package com.jugu.propertylease.main.contract.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 合同编号生成器。
 *
 * <p>格式：CT-{yyyyMM}-{seq}
 * <p>seq 在单节点内用 AtomicLong 保证唯一，多节点部署时如有冲突由 contract_no UNIQUE 约束兜底（重试）。
 * 命名风格与 {@code accounting.service.BillNoGenerator} 保持一致。
 */
@Component
public class ContractNoGenerator {

    private static final String PREFIX = "CT";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 100000);

    public String generate() {
        String month = LocalDate.now().format(FMT);
        return PREFIX + "-" + month + "-" + seq.incrementAndGet();
    }
}
