package com.jugu.propertylease.main.contract.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.*;
import static org.jooq.impl.DSL.trueCondition;

import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractChargeRule;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link ContractRepository} 的 jOOQ 实现。
 *
 * <p>⚠️ 重建说明：原文件在上一次会话打包时丢失（0 字节）。本文件根据
 * {@link ContractRepository} 接口方法签名 + 本仓库其它 Jooq*Repository（如
 * JooqOccupancyRepository / JooqAccountingRepository）一致的"方法参数名 = 字段名"
 * 命名规范重建。字段名与真实 014-create-contract-tables.xml 表结构如有出入，
 * 请对照 Liquibase changelog 核实列名（大概率只是大小写/下划线转换问题）。
 */
@Repository
public class JooqContractRepository implements ContractRepository {

    private final DSLContext dsl;

    public JooqContractRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ── Contract ────────────────────────────────────────────────────────────

    @Override
    public Long insertContract(String contractNo, Long enterpriseId, String status,
                               LocalDate startDate, LocalDate endDate, String paymentMode,
                               String remark, Long createdBy, OffsetDateTime now) {
        return dsl.insertInto(CONTRACT)
                .set(CONTRACT.CONTRACT_NO, contractNo)
                .set(CONTRACT.ENTERPRISE_ID, enterpriseId)
                .set(CONTRACT.STATUS, status)
                .set(CONTRACT.START_DATE, startDate)
                .set(CONTRACT.END_DATE, endDate)
                .set(CONTRACT.PAYMENT_MODE, paymentMode)
                .set(CONTRACT.REMARK, remark)
                .set(CONTRACT.CREATED_BY, createdBy)
                .set(CONTRACT.CREATED_AT, now)
                .set(CONTRACT.UPDATED_AT, now)
                .returning(CONTRACT.ID)
                .fetchOne(CONTRACT.ID);
    }

    @Override
    public Optional<Contract> findById(Long id) {
        return Optional.ofNullable(
                dsl.selectFrom(CONTRACT)
                        .where(CONTRACT.ID.eq(id))
                        .fetchOneInto(Contract.class));
    }

    @Override
    public Optional<Contract> findByContractNo(String contractNo) {
        return Optional.ofNullable(
                dsl.selectFrom(CONTRACT)
                        .where(CONTRACT.CONTRACT_NO.eq(contractNo))
                        .fetchOneInto(Contract.class));
    }

    @Override
    public void updateStatus(Long id, String status, OffsetDateTime now) {
        dsl.update(CONTRACT)
                .set(CONTRACT.STATUS, status)
                .set(CONTRACT.UPDATED_AT, now)
                .where(CONTRACT.ID.eq(id))
                .execute();
    }

    @Override
    public void updateSignBillId(Long id, Long signBillId, OffsetDateTime now) {
        dsl.update(CONTRACT)
                .set(CONTRACT.SIGN_BILL_ID, signBillId)
                .set(CONTRACT.UPDATED_AT, now)
                .where(CONTRACT.ID.eq(id))
                .execute();
    }

    @Override
    public List<Contract> findByFilter(Long enterpriseId, String status,
                                       LocalDate startDateFrom, LocalDate endDateTo,
                                       int offset, int limit) {
        Condition c = trueCondition();
        if (enterpriseId  != null) c = c.and(CONTRACT.ENTERPRISE_ID.eq(enterpriseId));
        if (status        != null) c = c.and(CONTRACT.STATUS.eq(status));
        if (startDateFrom != null) c = c.and(CONTRACT.START_DATE.ge(startDateFrom));
        if (endDateTo     != null) c = c.and(CONTRACT.END_DATE.le(endDateTo));
        return dsl.selectFrom(CONTRACT).where(c)
                .orderBy(CONTRACT.CREATED_AT.desc())
                .limit(limit).offset(offset)
                .fetchInto(Contract.class);
    }

    @Override
    public int countByFilter(Long enterpriseId, String status,
                             LocalDate startDateFrom, LocalDate endDateTo) {
        Condition c = trueCondition();
        if (enterpriseId  != null) c = c.and(CONTRACT.ENTERPRISE_ID.eq(enterpriseId));
        if (status        != null) c = c.and(CONTRACT.STATUS.eq(status));
        if (startDateFrom != null) c = c.and(CONTRACT.START_DATE.ge(startDateFrom));
        if (endDateTo     != null) c = c.and(CONTRACT.END_DATE.le(endDateTo));
        return dsl.fetchCount(CONTRACT, c);
    }

    @Override
    public List<Contract> findExpiringContracts(LocalDate date, List<String> activeStatuses) {
        return dsl.selectFrom(CONTRACT)
                .where(CONTRACT.END_DATE.le(date))
                .and(CONTRACT.STATUS.in(activeStatuses))
                .fetchInto(Contract.class);
    }

    // ── ContractRoom ────────────────────────────────────────────────────────

    @Override
    public Long insertContractRoom(Long contractId, Long roomId, BigDecimal signedRent,
                                   LocalDate leaseStart, LocalDate leaseEnd, OffsetDateTime now) {
        return dsl.insertInto(CONTRACT_ROOM)
                .set(CONTRACT_ROOM.CONTRACT_ID, contractId)
                .set(CONTRACT_ROOM.ROOM_ID, roomId)
                .set(CONTRACT_ROOM.SIGNED_RENT, signedRent)
                .set(CONTRACT_ROOM.LEASE_START, leaseStart)
                .set(CONTRACT_ROOM.LEASE_END, leaseEnd)
                .set(CONTRACT_ROOM.STATUS, "PENDING")
                .set(CONTRACT_ROOM.CREATED_AT, now)
                .set(CONTRACT_ROOM.UPDATED_AT, now)
                .returning(CONTRACT_ROOM.ID)
                .fetchOne(CONTRACT_ROOM.ID);
    }

    @Override
    public List<ContractRoom> findRoomsByContract(Long contractId, String status) {
        Condition c = CONTRACT_ROOM.CONTRACT_ID.eq(contractId);
        if (status != null) c = c.and(CONTRACT_ROOM.STATUS.eq(status));
        return dsl.selectFrom(CONTRACT_ROOM).where(c).fetchInto(ContractRoom.class);
    }

    @Override
    public Optional<ContractRoom> findRoomByContractAndRoom(Long contractId, Long roomId) {
        return Optional.ofNullable(
                dsl.selectFrom(CONTRACT_ROOM)
                        .where(CONTRACT_ROOM.CONTRACT_ID.eq(contractId))
                        .and(CONTRACT_ROOM.ROOM_ID.eq(roomId))
                        .fetchOneInto(ContractRoom.class));
    }

    @Override
    public Optional<ContractRoom> findRoomById(Long id) {
        return Optional.ofNullable(
                dsl.selectFrom(CONTRACT_ROOM)
                        .where(CONTRACT_ROOM.ID.eq(id))
                        .fetchOneInto(ContractRoom.class));
    }

    @Override
    public boolean existsActiveContractRoom(Long roomId) {
        return dsl.fetchExists(
                dsl.selectOne().from(CONTRACT_ROOM)
                        .where(CONTRACT_ROOM.ROOM_ID.eq(roomId))
                        .and(CONTRACT_ROOM.STATUS.eq("ACTIVE")));
    }

    @Override
    public void updateRoomStatus(Long contractRoomId, String status, OffsetDateTime returnedAt,
                                 OffsetDateTime now) {
        dsl.update(CONTRACT_ROOM)
                .set(CONTRACT_ROOM.STATUS, status)
                .set(CONTRACT_ROOM.RETURNED_AT, returnedAt)
                .set(CONTRACT_ROOM.UPDATED_AT, now)
                .where(CONTRACT_ROOM.ID.eq(contractRoomId))
                .execute();
    }

    @Override
    public int countActiveRoomsByContract(Long contractId) {
        return dsl.fetchCount(CONTRACT_ROOM,
                CONTRACT_ROOM.CONTRACT_ID.eq(contractId)
                        .and(CONTRACT_ROOM.STATUS.eq("ACTIVE")));
    }

    // ── ContractChargeRule ──────────────────────────────────────────────────

    @Override
    public void insertChargeRule(Long contractId, String chargeType, String payerType,
                                 BigDecimal amount, String ruleSnapshot, OffsetDateTime now) {
        dsl.insertInto(CONTRACT_CHARGE_RULE)
                .set(CONTRACT_CHARGE_RULE.CONTRACT_ID, contractId)
                .set(CONTRACT_CHARGE_RULE.CHARGE_TYPE, chargeType)
                .set(CONTRACT_CHARGE_RULE.PAYER_TYPE, payerType)
                .set(CONTRACT_CHARGE_RULE.AMOUNT, amount)
                .set(CONTRACT_CHARGE_RULE.RULE_SNAPSHOT, ruleSnapshot)
                .set(CONTRACT_CHARGE_RULE.CREATED_AT, now)
                .execute();
    }

    @Override
    public List<ContractChargeRule> findChargeRules(Long contractId) {
        return dsl.selectFrom(CONTRACT_CHARGE_RULE)
                .where(CONTRACT_CHARGE_RULE.CONTRACT_ID.eq(contractId))
                .fetchInto(ContractChargeRule.class);
    }
}
