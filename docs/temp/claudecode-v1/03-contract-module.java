// ─────────────────────────────────────────────────────────────────────────────
// ContractQueryPort.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.api;

import java.time.LocalDate;
import java.util.List;

public interface ContractQueryPort {

    ContractInfo getContract(Long contractId);

    /** schedule 触发合同到期检查时使用 */
    List<ContractInfo> findContractsDueBy(LocalDate date);

    /** schedule 触发租金账单生成时使用 */
    List<ContractInfo> findContractsNeedingRentBill(LocalDate date);

    List<ContractRoomInfo> getActiveRoomsByContract(Long contractId);

    /** meter 日结用：查询房间当前所有 CHECKED_IN 状态的 tenancy */
    List<TenancyInfo> getActiveTenanciesByRoom(Long roomId);

    List<TenancyInfo> getActiveTenanciesByContract(Long contractId);

    /** meter 日结用：查询有活跃 tenancy 的所有 room_id */
    List<Long> findRoomsWithActiveTenancies();

    /** meter 日结用：通过 roomId 获取 storeId（委托给 asset） */
    Long getStoreIdByRoom(Long roomId);

    /** meter 日结用：获取合同房间的 ROOM_SHARED 账户 ID */
    Long getSharedAccountId(Long roomId);
}

// ─────────────────────────────────────────────────────────────────────────────
// ContractScheduleTrigger.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.api;

import java.time.LocalDate;

public interface ContractScheduleTrigger {
    /** 检查并生成到期应出的租金账单 */
    void checkAndGenerateRentBills(LocalDate date);
    /** 检查合同到期，发送提醒或标记 TERMINATING */
    void checkContractExpiry(LocalDate date);
}

// ─────────────────────────────────────────────────────────────────────────────
// Contract Port Records
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ContractInfo(
    Long       id,
    String     contractNo,
    Long       enterpriseId,
    String     status,
    LocalDate  startDate,
    LocalDate  endDate,
    BigDecimal monthlyRent,
    String     paymentCycle,
    BigDecimal depositAmount,
    Long       depositBillId
) {}

public record ContractRoomInfo(
    Long   id,
    Long   contractId,
    Long   roomId,
    String status,
    Long   sharedAccountId
) {}

public record TenancyInfo(
    Long           id,
    Long           contractId,
    Long           contractRoomId,
    Long           roomId,
    Long           employeeId,
    Long           iamUserId,
    Long           tenantAccountId,
    String         status,
    OffsetDateTime checkInAt
) {}

// ─────────────────────────────────────────────────────────────────────────────
// ContractLifecycleService.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.account.api.AccountCommandPort;
import com.jugu.propertylease.main.account.api.OpenAccountCommand;
import com.jugu.propertylease.main.asset.api.AssetCommandPort;
import com.jugu.propertylease.main.contract.api.*;
import com.jugu.propertylease.main.contract.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ContractLifecycleService {

    private final ContractRepository contractRepo;
    private final ContractRoomRepository contractRoomRepo;
    private final ContractOperationLogRepository opLogRepo;
    private final AccountCommandPort accountCommandPort;
    private final AssetCommandPort assetCommandPort;
    // 注入 billingServiceClient（调 Billing Service 生成押金账单）

    // 构造器注入（省略）

    public Object createContract(/* CreateContractRequest */ Object req) {
        // 1. 校验 enterpriseId 存在（通过 EnterpriseQueryPort）
        // 2. 校验 startDate < endDate
        // 3. 生成合同编号 CTR-{yyyyMM}-{seq}
        // 4. 持久化 contract（status=DRAFT）
        // 5. 写操作日志 CREATED
        throw new UnsupportedOperationException("TODO: implement");
    }

    public Object signContract(Long contractId, String termsDocUrl) {
        Object contract = contractRepo.findById(contractId)
            .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));
        // 1. 校验 status == DRAFT
        assertStatus(contract, "DRAFT", "签约");
        // 2. 调 Billing Service 生成押金账单
        //    Long depositBillId = billingServiceClient.createDepositBill(contractId, depositAmount)
        // 3. 更新 status=SIGNED，deposit_bill_id, terms_doc_url
        // 4. 写操作日志 SIGNED
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * 激活合同：SIGNED → READY_FOR_ALLOCATION。
     * 为每个 contractRoom 创建 ROOM_SHARED 水电账户。
     * 由押金支付回调或手动激活触发，接口幂等。
     */
    public Object activateContract(Long contractId) {
        Object contract = contractRepo.findById(contractId)
            .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));
        // 1. 校验 status == SIGNED
        assertStatus(contract, "SIGNED", "激活");
        // 2. 校验押金账单已支付（deposit_paid_at != null 或通过 Billing Service 查询）
        // 3. 为每个 contractRoom 创建 ROOM_SHARED 账户
        List<Object> rooms = contractRoomRepo.findByContractId(contractId);
        for (Object room : rooms) {
            // Long contractRoomId = room.id
            // accountCommandPort.openAccount(new OpenAccountCommand(
            //     "ROOM_SHARED", "CONTRACT_ROOM", contractRoomId, operatorId))
            // contractRoomRepo.updateSharedAccountId(room.id, accountInfo.id())
        }
        // 4. 更新 status = READY_FOR_ALLOCATION
        // 5. 写操作日志 ACTIVATED
        throw new UnsupportedOperationException("TODO: implement");
    }

    public Object cancelContract(Long contractId, String reason, Long operatorId) {
        // 1. 校验 status IN (DRAFT, SIGNED)
        // 2. 更新 status=CANCELLED, cancel_reason
        // 3. 写操作日志 CANCELLED
        throw new UnsupportedOperationException("TODO: implement");
    }

    public Object terminateContract(Long contractId, String reason, Long operatorId) {
        // 1. 校验 status == IN_PROGRESS
        // 2. 更新 status = TERMINATING, terminate_reason
        // 3. 写操作日志 TERMINATING
        // 注意：不立即 TERMINATED，等所有 tenancy 退宿结算完成后由回调驱动
        throw new UnsupportedOperationException("TODO: implement");
    }

    private void assertStatus(Object contract, String expected, String operation) {
        // 从 contract 读取 status，不匹配则抛 BusinessException
        // "合同当前状态不支持 {operation} 操作，当前状态：{status}，要求状态：{expected}"
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CheckInService.java  ← 入住编排核心（跨模块调用，@Transactional）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.account.api.*;
import com.jugu.propertylease.main.asset.api.AssetCommandPort;
import com.jugu.propertylease.main.asset.api.AssetQueryPort;
import com.jugu.propertylease.main.contract.api.*;
import com.jugu.propertylease.main.contract.repo.*;
import com.jugu.propertylease.main.enterprise.api.EnterpriseQueryPort;
import com.jugu.propertylease.main.iam.api.IamTenantPort;
import com.jugu.propertylease.main.iam.api.CreateTenantUserCommand;
import com.jugu.propertylease.main.meter.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@Transactional
public class CheckInService {

    private final ContractRepository contractRepo;
    private final ContractRoomRepository contractRoomRepo;
    private final TenancyRepository tenancyRepo;
    private final ContractOperationLogRepository opLogRepo;

    private final AssetQueryPort assetQueryPort;
    private final AssetCommandPort assetCommandPort;
    private final EnterpriseQueryPort enterpriseQueryPort;
    private final IamTenantPort iamTenantPort;
    private final MeterCommandPort meterCommandPort;
    private final AccountCommandPort accountCommandPort;

    // 构造器注入（省略）

    /**
     * 办理员工入住。
     *
     * <p>整体 @Transactional，任一步骤失败则全部回滚。
     * IAM / meter / account 调用均通过 Interface，未来微服务化时改为远程调用 + saga。
     */
    public TenancyInfo checkIn(
        Long contractId, Long roomId, Long employeeId,
        LocalDate expectedCheckOutDate,
        BigDecimal waterReading, BigDecimal electricityReading,
        Long operatorId
    ) {
        // ── 前置校验 ──────────────────────────────────────────────
        Object contract = contractRepo.findById(contractId)
            .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));

        assertContractStatus(contract, "checkIn 要求合同处于 READY_FOR_ALLOCATION 或 IN_PROGRESS");

        Object contractRoom = contractRoomRepo.findActiveByContractAndRoom(contractId, roomId)
            .orElseThrow(() -> new BusinessException("CONTRACT_ROOM_NOT_FOUND",
                "房间未分配到该合同，roomId=" + roomId));

        // 员工必须属于该企业
        Long enterpriseId = getEnterpriseId(contract);
        if (!enterpriseQueryPort.isEmployeeOfEnterprise(employeeId, enterpriseId)) {
            throw new BusinessException("EMPLOYEE_NOT_IN_ENTERPRISE",
                "员工不属于该企业，employeeId=" + employeeId);
        }

        // 员工不能有其他活跃入住
        tenancyRepo.findActiveByEmployee(employeeId).ifPresent(existing -> {
            throw new BusinessException("EMPLOYEE_ALREADY_CHECKED_IN",
                "员工已有入住记录，tenancyId=" + existing);
        });

        // ── 跨模块编排 ────────────────────────────────────────────

        // Step 1: 更新房间状态 PENDING_CHECKING → OCCUPIED（或 ALLOCATED → OCCUPIED）
        assetCommandPort.assignTenant(roomId, null /* tenancyId 稍后填 */);

        // Step 2: 创建 IAM TENANT 用户
        var employee = enterpriseQueryPort.getEmployee(employeeId);
        Long iamUserId = iamTenantPort.createTenantUser(new CreateTenantUserCommand(
            employeeId, enterpriseId, employee.name(), employee.mobile()
        ));

        // Step 3: 记录入住水表底数
        meterCommandPort.recordCheckInReading(new RecordReadingCommand(
            roomId, "WATER", waterReading,
            OffsetDateTime.now(), "MANUAL", "CHECK_IN",
            null /* tenancyId 后填 */, null, operatorId
        ));

        // Step 4: 记录入住电表底数
        meterCommandPort.recordCheckInReading(new RecordReadingCommand(
            roomId, "ELECTRICITY", electricityReading,
            OffsetDateTime.now(), "MANUAL", "CHECK_IN",
            null /* tenancyId 后填 */, null, operatorId
        ));

        // Step 5: 开户（个人水电账户）
        Long contractRoomId = getContractRoomId(contractRoom);
        AccountInfo tenantAccount = accountCommandPort.openAccount(new OpenAccountCommand(
            "TENANT", "TENANCY", null /* tenancyId 后填 */, operatorId
        ));

        // Step 6: 持久化 tenancy（CHECKED_IN）
        Long tenancyId = tenancyRepo.insert(
            contractId, contractRoomId, roomId, employeeId,
            iamUserId, tenantAccount.id(),
            "CHECKED_IN", OffsetDateTime.now(), expectedCheckOutDate,
            operatorId
        );

        // Step 6b: 更新 meter_reading 和 account 的 tenancyId（若需要补填）
        // meterCommandPort.updateTenancyId(roomId, tenancyId, "CHECK_IN") — 视实现决定

        // Step 7: 若合同是 READY_FOR_ALLOCATION，升级为 IN_PROGRESS
        upgradeContractToInProgressIfNeeded(contract, contractId);

        // Step 8: 写操作日志
        opLogRepo.insert(contractId, "TENANT_CHECKED_IN", tenancyId, operatorId,
            "员工 " + employeeId + " 入住房间 " + roomId);

        return tenancyRepo.findById(tenancyId)
            .map(this::toTenancyInfo)
            .orElseThrow();
    }

    private void assertContractStatus(Object contract, String message) {
        // status must be IN (READY_FOR_ALLOCATION, IN_PROGRESS)
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }

    private Long getEnterpriseId(Object contract) {
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }

    private Long getContractRoomId(Object contractRoom) {
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }

    private void upgradeContractToInProgressIfNeeded(Object contract, Long contractId) {
        // if status == READY_FOR_ALLOCATION → update to IN_PROGRESS
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }

    private TenancyInfo toTenancyInfo(Object record) {
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CheckoutService.java  ← 退宿编排
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.asset.api.AssetCommandPort;
import com.jugu.propertylease.main.contract.api.TenancyInfo;
import com.jugu.propertylease.main.contract.repo.*;
import com.jugu.propertylease.main.iam.api.IamTenantPort;
import com.jugu.propertylease.main.meter.api.*;
import com.jugu.propertylease.main.settlement.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@Transactional
public class CheckoutService {

    private final TenancyRepository tenancyRepo;
    private final ContractRoomRepository contractRoomRepo;
    private final ContractRepository contractRepo;
    private final ContractOperationLogRepository opLogRepo;

    private final MeterCommandPort meterCommandPort;
    private final SettlementCommandPort settlementCommandPort;
    private final AssetCommandPort assetCommandPort;
    private final IamTenantPort iamTenantPort;

    // 构造器注入（省略）

    /**
     * 发起退宿（CHECKED_IN → CHECKING_OUT），触发异步结算。
     * 结算完成后由 Settlement 回调 /internal/v1/contract/tenancy-settlement-complete。
     */
    public TenancyInfo initiateCheckout(
        Long tenancyId,
        BigDecimal waterReading,
        BigDecimal electricityReading,
        Long operatorId
    ) {
        Object tenancy = tenancyRepo.findById(tenancyId)
            .orElseThrow(() -> new BusinessException("TENANCY_NOT_FOUND", "入住记录不存在"));

        assertTenancyStatus(tenancy, "CHECKED_IN", "发起退宿");

        Long roomId = getRoomId(tenancy);
        Long contractId = getContractId(tenancy);

        // Step 1: 更新 tenancy → CHECKING_OUT
        tenancyRepo.updateStatus(tenancyId, "CHECKING_OUT", null);

        // Step 2: 记录退宿水表读数
        meterCommandPort.recordCheckOutReading(new RecordReadingCommand(
            roomId, "WATER", waterReading,
            OffsetDateTime.now(), "MANUAL", "CHECK_OUT",
            tenancyId, null, operatorId
        ));

        // Step 3: 记录退宿电表读数
        meterCommandPort.recordCheckOutReading(new RecordReadingCommand(
            roomId, "ELECTRICITY", electricityReading,
            OffsetDateTime.now(), "MANUAL", "CHECK_OUT",
            tenancyId, null, operatorId
        ));

        // Step 4: 发起结算（异步，完成后回调 contract）
        settlementCommandPort.initiateCheckout(new InitiateCheckoutCommand(
            tenancyId, contractId,
            getContractRoomId(tenancy), roomId,
            getEmployeeId(tenancy), getTenantAccountId(tenancy)
        ));

        // Step 5: 写操作日志
        opLogRepo.insert(contractId, "TENANT_CHECKOUT_INITIATED", tenancyId, operatorId,
            "发起退宿，roomId=" + roomId);

        return tenancyRepo.findById(tenancyId).map(this::toTenancyInfo).orElseThrow();
    }

    /**
     * 退宿结算完成回调处理（由 InternalContractApiDelegateImpl 调用）。
     * 释放房间、删除 IAM 用户、检查是否触发合同终止结算。
     */
    @Transactional
    public void onTenancySettlementComplete(Long tenancyId, Long settlementId) {
        Object tenancy = tenancyRepo.findById(tenancyId)
            .orElseThrow(() -> new BusinessException("TENANCY_NOT_FOUND", "入住记录不存在"));

        Long roomId = getRoomId(tenancy);
        Long contractId = getContractId(tenancy);
        Long iamUserId = getIamUserId(tenancy);
        Long contractRoomId = getContractRoomId(tenancy);

        // Step 1: tenancy → CHECKED_OUT
        tenancyRepo.updateStatus(tenancyId, "CHECKED_OUT", OffsetDateTime.now());

        // Step 2: 释放 asset 房间状态
        assetCommandPort.releaseTenant(roomId, tenancyId);

        // Step 3: 删除 IAM TENANT 用户
        if (iamUserId != null) {
            iamTenantPort.deleteTenantUser(iamUserId);
        }

        // Step 4: 检查 contractRoom 是否所有 tenancy 均已 CHECKED_OUT
        boolean allCheckedOut = tenancyRepo.countActiveByContractRoom(contractRoomId) == 0;
        if (allCheckedOut) {
            contractRoomRepo.updateStatus(contractRoomId, "RELEASED", OffsetDateTime.now());
            assetCommandPort.releaseRoom(roomId, contractId);
        }

        // Step 5: 检查合同是否处于 TERMINATING，且所有 contractRoom 均 RELEASED
        Object contract = contractRepo.findById(contractId).orElseThrow();
        boolean isTerminating = "TERMINATING".equals(getContractStatus(contract));
        boolean allRoomsReleased = contractRoomRepo.countActiveByContract(contractId) == 0;

        if (isTerminating && allRoomsReleased) {
            // 触发合同终止结算（押金处理、ROOM_SHARED 账户余额清算）
            settlementCommandPort.initiateContractTermination(contractId);
        }

        // Step 6: 写操作日志
        opLogRepo.insert(contractId, "TENANT_CHECKED_OUT", tenancyId, null,
            "结算完成，settlementId=" + settlementId);
    }

    private void assertTenancyStatus(Object tenancy, String expected, String operation) {
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }

    private Long getRoomId(Object tenancy)           { throw new UnsupportedOperationException("TODO"); }
    private Long getContractId(Object tenancy)       { throw new UnsupportedOperationException("TODO"); }
    private Long getContractRoomId(Object tenancy)   { throw new UnsupportedOperationException("TODO"); }
    private Long getEmployeeId(Object tenancy)       { throw new UnsupportedOperationException("TODO"); }
    private Long getTenantAccountId(Object tenancy)  { throw new UnsupportedOperationException("TODO"); }
    private Long getIamUserId(Object tenancy)        { throw new UnsupportedOperationException("TODO"); }
    private String getContractStatus(Object contract){ throw new UnsupportedOperationException("TODO"); }
    private TenancyInfo toTenancyInfo(Object record) { throw new UnsupportedOperationException("TODO"); }
}

// ─────────────────────────────────────────────────────────────────────────────
// RentBillService.java  ← 租金账单生成（ContractScheduleTrigger 实现）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.service;

import com.jugu.propertylease.main.contract.api.*;
import com.jugu.propertylease.main.contract.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class RentBillService implements ContractScheduleTrigger {

    private static final Logger log = LoggerFactory.getLogger(RentBillService.class);

    private final ContractRepository contractRepo;
    private final ContractRentBillRepository rentBillRepo;
    // 注入 billingServiceClient

    // 构造器注入（省略）

    @Override
    @Transactional
    public void checkAndGenerateRentBills(LocalDate date) {
        // 1. 查询所有 IN_PROGRESS 状态的合同
        //    找出：下一期账单周期的 period_start <= date，且尚未生成账单的合同
        List<ContractInfo> contracts = contractRepo.findContractsNeedingRentBill(date);
        for (ContractInfo c : contracts) {
            try {
                generateRentBill(c, date);
            } catch (Exception e) {
                log.error("租金账单生成失败 contractId={}", c.id(), e);
                // 单个失败不影响其他合同
            }
        }
    }

    @Transactional
    protected void generateRentBill(ContractInfo contract, LocalDate date) {
        // 1. 计算本期 period_start / period_end（按 paymentCycle 推算）
        LocalDate periodStart = calculatePeriodStart(contract, date);
        LocalDate periodEnd = calculatePeriodEnd(contract, periodStart);

        // 2. 幂等检查：(contractId, periodStart) 唯一索引
        if (rentBillRepo.exists(contract.id(), periodStart)) {
            return;
        }

        // 3. 调 Billing Service 生成租金账单
        //    Long billId = billingServiceClient.createRentBill(contract.id(), amount, periodStart, periodEnd)

        // 4. 写 contract_rent_bill（status=PENDING）
        //    rentBillRepo.insert(contractId, billId, periodStart, periodEnd, amount, "PENDING")
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void checkContractExpiry(LocalDate date) {
        // 1. 查询 end_date <= date + 7天（7天内到期）且 status = IN_PROGRESS 的合同
        // 2. 发送到期提醒通知（调 Notification Service 或写通知记录）
        // 3. end_date < date（已过期）的合同：仅告警，不自动终止
        //    （需要管理员手动 terminate）
        throw new UnsupportedOperationException("TODO: implement");
    }

    private LocalDate calculatePeriodStart(ContractInfo contract, LocalDate date) {
        // 根据 paymentCycle（MONTHLY/QUARTERLY）和已有账单记录推算下一期起始
        throw new UnsupportedOperationException("TODO: implement");
    }

    private LocalDate calculatePeriodEnd(ContractInfo contract, LocalDate periodStart) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IamTenantPort.java（IAM 模块新增 Port，放在 iam.api 包）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.iam.api;

public interface IamTenantPort {

    /**
     * 入住时创建 TENANT 类型 IAM 用户。
     * 幂等：若该 mobile 已有用户则复用，返回 iamUserId。
     */
    Long createTenantUser(CreateTenantUserCommand cmd);

    /**
     * 退宿后软删除 TENANT 用户。
     * 利用 IAM 模块现有的 UserLifecycleService.softDelete() 实现。
     */
    void deleteTenantUser(Long iamUserId);
}

// ─────────────────────────────────────────────────────────────────────────────
// CreateTenantUserCommand.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.iam.api;

public record CreateTenantUserCommand(
    Long   employeeId,
    Long   enterpriseId,
    String realName,
    String mobile
) {}

// ─────────────────────────────────────────────────────────────────────────────
// IamTenantPortImpl.java（IAM 模块内实现，放在 iam.service 包）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.main.iam.api.*;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IamTenantPortImpl implements IamTenantPort {

    private final UserRepository userRepo;
    private final UserLifecycleService userLifecycleService;

    public IamTenantPortImpl(UserRepository userRepo, UserLifecycleService userLifecycleService) {
        this.userRepo = userRepo;
        this.userLifecycleService = userLifecycleService;
    }

    @Override
    public Long createTenantUser(CreateTenantUserCommand cmd) {
        // 1. 幂等：通过 mobile 查找已存在的 TENANT 用户
        return userRepo.findByMobileAndUserType(cmd.mobile(), "TENANT")
            .map(existing -> (Long) existing)  // 已存在则直接复用
            .orElseGet(() -> {
                // 2. 不存在则创建（复用 InternalTenantUserService 已有逻辑）
                // userLifecycleService.createTenantUser(cmd.realName(), cmd.mobile(), ...)
                throw new UnsupportedOperationException("TODO: 复用 InternalTenantUserService");
            });
    }

    @Override
    public void deleteTenantUser(Long iamUserId) {
        // 复用 UserLifecycleService.softDelete()
        userLifecycleService.softDelete(iamUserId, "退宿，自动注销", null);
    }
}
