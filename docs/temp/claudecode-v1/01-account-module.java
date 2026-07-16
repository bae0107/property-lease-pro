// ─────────────────────────────────────────────────────────────────────────────
// AccountCommandPort.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.api;

import java.math.BigDecimal;
import java.util.List;

public interface AccountCommandPort {

    /** 开户（由 contract 编排入住/激活时调用） */
    AccountInfo openAccount(OpenAccountCommand cmd);

    /**
     * 销户。
     * 仅允许余额和冻结余额均为 0 时销户，否则抛异常。
     * 返回销户时剩余余额（调用方用于退款判断）。
     */
    CloseAccountResult closeAccount(Long accountId, String reason);

    /**
     * 扣款。
     * 余额不足时不抛异常，扣至 0，返回 shortfall。
     * 调用方根据 shortfall 决定后续行为（标记欠费 / 生成追缴账单）。
     */
    DeductResult deduct(DeductCommand cmd);

    /** 充值（由充值账单支付成功回调触发；接口幂等，billId 为幂等键） */
    void topUp(TopUpCommand cmd);

    /** 冻结余额（结算开始时调用，防止重复扣款） */
    FreezeResult freeze(Long accountId, BigDecimal amount, String reason);

    /** 解冻余额（结算取消或异常回滚时调用） */
    void unfreeze(Long accountId, BigDecimal amount, String reason);
}

// ─────────────────────────────────────────────────────────────────────────────
// AccountQueryPort.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountQueryPort {

    Optional<AccountInfo> findAccountByOwner(String ownerType, Long ownerId);

    AccountInfo getAccountByOwner(String ownerType, Long ownerId);

    List<AccountInfo> getAccountsByOwners(String ownerType, List<Long> ownerIds);

    BigDecimal getAvailableBalance(Long accountId);
}

// ─────────────────────────────────────────────────────────────────────────────
// Account Port Records（Command / Result）
// 放在 api 包下，与 Port 接口同包
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OpenAccountCommand(
    String accountType,   // ROOM_SHARED | TENANT
    String ownerType,     // CONTRACT_ROOM | TENANCY
    Long   ownerId,
    Long   createdBy
) {}

public record DeductCommand(
    Long       accountId,
    BigDecimal amount,
    String     referenceType,  // METER_SETTLEMENT | SETTLEMENT | MANUAL
    Long       referenceId,
    String     note
) {}

public record DeductResult(
    boolean    success,     // true = 至少扣了部分金额
    BigDecimal deducted,    // 实际扣除金额
    BigDecimal shortfall    // 欠费金额（0 = 足额扣款）
) {}

public record TopUpCommand(
    Long       accountId,
    BigDecimal amount,
    Long       billingBillId,  // 充值账单 ID（幂等键）
    Long       operatedBy
) {}

public record FreezeResult(
    boolean    success,
    BigDecimal frozen
) {}

public record CloseAccountResult(
    BigDecimal remainingBalance  // 销户时剩余余额（通常应为 0）
) {}

public record AccountInfo(
    Long       id,
    String     accountNo,
    String     accountType,
    String     ownerType,
    Long       ownerId,
    BigDecimal balance,
    BigDecimal frozenBalance,
    String     status
) {}

// ─────────────────────────────────────────────────────────────────────────────
// AccountLifecycleService.java（Service 骨架）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.account.api.*;
import com.jugu.propertylease.main.account.repo.AccountRepository;
import com.jugu.propertylease.main.account.repo.AccountTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountLifecycleService {

    private final AccountRepository accountRepo;
    private final AccountTransactionRepository txnRepo;
    private final AccountNoGenerator accountNoGenerator;  // 账号序号生成

    public AccountLifecycleService(
        AccountRepository accountRepo,
        AccountTransactionRepository txnRepo,
        AccountNoGenerator accountNoGenerator
    ) {
        this.accountRepo = accountRepo;
        this.txnRepo = txnRepo;
        this.accountNoGenerator = accountNoGenerator;
    }

    public AccountInfo openAccount(OpenAccountCommand cmd) {
        // 1. 校验：同一 (ownerType, ownerId) 是否已有非 CLOSED 账户
        accountRepo.findActiveByOwner(cmd.ownerType(), cmd.ownerId())
            .ifPresent(existing -> {
                throw new BusinessException("ACCOUNT_ALREADY_EXISTS",
                    "账户已存在：ownerType=" + cmd.ownerType() + ", ownerId=" + cmd.ownerId());
            });

        // 2. 生成账号 ACC-{TYPE_PREFIX}-{yyyyMMdd}-{seq}
        String accountNo = accountNoGenerator.generate(cmd.accountType());

        // 3. 持久化
        // return accountRepo.insert(...) → AccountInfo
        throw new UnsupportedOperationException("TODO: implement");
    }

    public CloseAccountResult closeAccount(Long accountId, String reason) {
        // 1. 查账户，检查 status != CLOSED
        // 2. 检查 balance == 0 && frozenBalance == 0（否则业务异常）
        // 3. 更新 status = CLOSED
        // 4. 写操作流水 (ADJUST, direction=OUT if balance>0 residual handled by caller)
        throw new UnsupportedOperationException("TODO: implement");
    }

    public FreezeResult freeze(Long accountId, BigDecimal amount, String reason) {
        // SELECT FOR UPDATE accountId
        // balance -= min(amount, balance)
        // frozen_balance += frozen
        // 写流水 FREEZE
        throw new UnsupportedOperationException("TODO: implement");
    }

    public void unfreeze(Long accountId, BigDecimal amount, String reason) {
        // SELECT FOR UPDATE accountId
        // frozen_balance -= amount
        // balance += amount
        // 写流水 UNFREEZE
        throw new UnsupportedOperationException("TODO: implement");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AccountDeductService.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.service;

import com.jugu.propertylease.main.account.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountDeductService {

    // 注入 accountRepo, txnRepo

    public DeductResult deduct(DeductCommand cmd) {
        // 1. SELECT FOR UPDATE account by id
        // 2. 校验 status == ACTIVE（FROZEN/CLOSED 抛异常）
        // 3. actualDeduct = min(cmd.amount(), account.balance)
        // 4. account.balance -= actualDeduct
        // 5. shortfall = cmd.amount() - actualDeduct
        // 6. 写流水 DEDUCT, direction=OUT
        // 7. 返回 DeductResult(success=actualDeduct>0, deducted=actualDeduct, shortfall=shortfall)
        throw new UnsupportedOperationException("TODO: implement");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AccountTopUpService.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.service;

import com.jugu.propertylease.main.account.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountTopUpService {

    // 注入 accountRepo, txnRepo, billingServiceClient

    /** 发起充值：调 Billing Service 生成充值账单，返回 billId + paymentUrl */
    public TopUpInitiateResult initiateTopUp(Long accountId, java.math.BigDecimal amount, String paymentMethod) {
        // 1. 查账户，校验 status == ACTIVE
        // 2. 调 Billing Service: createTopUpBill(accountId, amount, paymentMethod)
        // 3. 返回 {billId, paymentUrl}
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * 处理充值回调（幂等）。
     * 幂等键：account_transaction.reference_type=TOP_UP_BILL + reference_id=billId 唯一索引。
     */
    public void handleTopUpCallback(TopUpCommand cmd) {
        // 1. 检查是否已存在该 billId 的 TOP_UP_BILL 流水（唯一索引），已存在则直接返回
        // 2. SELECT FOR UPDATE account
        // 3. account.balance += cmd.amount()
        // 4. 写流水 TOP_UP, direction=IN, reference_type=TOP_UP_BILL, reference_id=billId
        throw new UnsupportedOperationException("TODO: implement");
    }

    record TopUpInitiateResult(Long billId, String paymentUrl) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// AccountCommandPortImpl.java（Port 实现，组合各 Service）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.service;

import com.jugu.propertylease.main.account.api.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountCommandPortImpl implements AccountCommandPort {

    private final AccountLifecycleService lifecycleService;
    private final AccountDeductService deductService;
    private final AccountTopUpService topUpService;

    public AccountCommandPortImpl(
        AccountLifecycleService lifecycleService,
        AccountDeductService deductService,
        AccountTopUpService topUpService
    ) {
        this.lifecycleService = lifecycleService;
        this.deductService = deductService;
        this.topUpService = topUpService;
    }

    @Override public AccountInfo openAccount(OpenAccountCommand cmd) {
        return lifecycleService.openAccount(cmd);
    }

    @Override public CloseAccountResult closeAccount(Long accountId, String reason) {
        return lifecycleService.closeAccount(accountId, reason);
    }

    @Override public DeductResult deduct(DeductCommand cmd) {
        return deductService.deduct(cmd);
    }

    @Override public void topUp(TopUpCommand cmd) {
        topUpService.handleTopUpCallback(cmd);
    }

    @Override public FreezeResult freeze(Long accountId, BigDecimal amount, String reason) {
        return lifecycleService.freeze(accountId, amount, reason);
    }

    @Override public void unfreeze(Long accountId, BigDecimal amount, String reason) {
        lifecycleService.unfreeze(accountId, amount, reason);
    }
}
