package com.jugu.propertylease.main.accounting.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 账单编号生成器。
 *
 * <p>格式：{TYPE_PREFIX}-{yyyyMM}-{seq}
 * <p>seq 在单节点内用 AtomicLong 保证唯一，多节点部署时如有冲突由 bill_no UNIQUE 约束兜底（重试）。
 */
@Component
public class BillNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 100000);

    public String generate(String typePrefix) {
        String month = LocalDate.now().format(FMT);
        return typePrefix + "-" + month + "-" + seq.incrementAndGet();
    }

    /** 预定义类型前缀，与 bill_type 对应。*/
    public static final class Prefix {
        public static final String SIGN       = "ESB";   // ENTERPRISE_SIGN_BILL
        public static final String DEPOSIT    = "PDB";   // PERSONAL_DEPOSIT_BILL
        public static final String RECHARGE   = "RCH";   // RECHARGE_BILL
        public static final String SETTLEMENT = "STL";   // SETTLEMENT_BILL
        public static final String REFUND     = "RFD";   // REFUND_BILL
    }
}
