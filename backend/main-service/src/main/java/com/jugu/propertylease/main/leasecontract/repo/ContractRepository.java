package com.jugu.propertylease.main.leasecontract.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractChargeRule;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ContractRepository {

    // ── Contract ────────────────────────────────────────────────────────────

    Long insertContract(String contractNo, Long enterpriseId, String status,
                        LocalDate startDate, LocalDate endDate, String paymentMode,
                        String remark, Long createdBy, OffsetDateTime now);

    /** 续租生成的新合同：额外写入 renewed_from_contract_id。*/
    Long insertRenewalContract(String contractNo, Long enterpriseId, String status,
                               LocalDate startDate, LocalDate endDate, String paymentMode,
                               String remark, Long renewedFromContractId,
                               Long createdBy, OffsetDateTime now);

    Optional<Contract> findById(Long id);

    Optional<Contract> findByContractNo(String contractNo);

    void updateStatus(Long id, String status, OffsetDateTime now);

    void updateSignBillId(Long id, Long signBillId, OffsetDateTime now);

    List<Contract> findByFilter(Long enterpriseId, String status,
                                LocalDate startDateFrom, LocalDate endDateTo,
                                int offset, int limit);

    int countByFilter(Long enterpriseId, String status,
                      LocalDate startDateFrom, LocalDate endDateTo);

    /** schedule 用：查询 end_date <= date 且 status 为活跃的合同。*/
    List<Contract> findExpiringContracts(LocalDate date, List<String> activeStatuses);

    /** schedule 用：按状态集合查合同（月租账单生成）。*/
    List<Contract> findByStatuses(List<String> statuses);

    // ── ContractRoom ────────────────────────────────────────────────────────

    Long insertContractRoom(Long contractId, Long roomId, BigDecimal signedRent,
                            LocalDate leaseStart, LocalDate leaseEnd, OffsetDateTime now);

    List<ContractRoom> findRoomsByContract(Long contractId, String status);

    Optional<ContractRoom> findRoomByContractAndRoom(Long contractId, Long roomId);

    /** 按 contract_room.id 反查单条记录（换宿场景：请求只携带 contractRoomId）。*/
    Optional<ContractRoom> findRoomById(Long id);

    /** 校验房间是否已在某活跃合同中（SIGN_BILL_PENDING 以上）。*/
    boolean existsActiveContractRoom(Long roomId);

    void updateRoomStatus(Long contractRoomId, String status, OffsetDateTime returnedAt,
                          OffsetDateTime now);

    int countActiveRoomsByContract(Long contractId);

    // ── ContractChargeRule ──────────────────────────────────────────────────

    void insertChargeRule(Long contractId, String chargeType, String payerType,
                          BigDecimal amount, String ruleSnapshot, OffsetDateTime now);

    List<ContractChargeRule> findChargeRules(Long contractId);
}
