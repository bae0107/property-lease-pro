package com.jugu.propertylease.main.leasecontract.service;

import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractChargeRule;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;

import java.util.List;

/**
 * {@link ContractLifecycleService} 各写操作的返回载体（合同 + 房间 + 计费规则）。
 *
 * <p>⚠️ 这是纯 Java record，未绑定任何 OpenAPI 生成的 API model —— 因为本轮没有拿到
 * 真正的 v5 版 contract-external.yaml（上传包里的 contract-external.yaml 是 v4 旧版，
 * 路径/schema 都对不上，比如还在用 /contract/tenancies）。等你补上真正的 v5 yaml 后，
 * Delegate 层直接从这个 record 转换成对应的 API model 即可。
 */
public record ContractDetail(
        Contract contract,
        List<ContractRoom> rooms,
        List<ContractChargeRule> chargeRules
) {}
