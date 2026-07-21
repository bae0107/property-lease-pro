package com.jugu.propertylease.main.accounting.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.model.*;
import com.jugu.propertylease.main.accounting.outer.BillingServicePort;
import com.jugu.propertylease.main.accounting.repo.*;
import com.jugu.propertylease.main.accounting.repo.jooq.JooqAccountingRepository;
import com.jugu.propertylease.main.contract.api.ContractCallbackPort;
import com.jugu.propertylease.main.jooq.tables.pojos.Bill;
import com.jugu.propertylease.main.jooq.tables.pojos.DepositLedger;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccount;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountSubBalance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * accounting 模块核心业务逻辑。
 *
 * <p>此 Service 实现了 AccountingCommandPort 和 AccountingQueryPort 的全部方法，
 * 并承载外部 API（充值、账单查询等）的业务逻辑。
 *
 * <p>ContractCallbackPort 通过构造器延迟注入（Spring lazy proxy）
 * 避免与 contract 模块产生循环依赖。
 */
@Service
public class AccountingService {

    private final JooqAccountingRepository repo;
    private final BillingServicePort billingServicePort;
    private final ContractCallbackPort contractCallbackPort;
    private final BillNoGenerator billNoGenerator;

    public AccountingService(JooqAccountingRepository repo,
                             BillingServicePort billingServicePort,
                             ContractCallbackPort contractCallbackPort,
                             BillNoGenerator billNoGenerator) {
        this.repo = repo;
        this.billingServicePort = billingServicePort;
        this.contractCallbackPort = contractCallbackPort;
        this.billNoGenerator = billNoGenerator;
    }

    // ══════════════════════════════════════════════════════════════════════
    // AccountingCommandPort 实现
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public CreateBillResult createEnterpriseSignBill(EnterpriseSignBillCommand cmd) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1.
      // （confirmContract 时可能尚未入住，先激活账户）
        // 注：房间列表由 contract 模块在调用前组装，此处按 contractId 统一处理
        // 房间账户在首次入住或签约时 upsert（合同维度的账户在此创建）
        // 实际房间账户由 occupancy.checkIn 时 upsertRoomAccount 创建；
        // 签约账单只需要记录账单本身和企业押金台账

        // 2. 创建账单记录
        String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.SIGN);
        Long billId = repo.insert(billNo, "ENTERPRISE_SIGN_BILL",
                "ENTERPRISE", cmd.enterpriseId(),
                cmd.contractId(), null, null,
                cmd.totalAmount(), null, now);

        // 3. 创建企业押金台账 (PENDING_PAYMENT)
        if (cmd.depositAmount() != null && cmd.depositAmount().compareTo(BigDecimal.ZERO) > 0) {
            repo.insert("ENTERPRISE", "ENTERPRISE", cmd.enterpriseId(),
                    cmd.contractId(), null, cmd.depositAmount(),
                    "PENDING_PAYMENT", billId, now);
        }

        // 4. 调用 billing-service（stub）
        var billingResult = billingServicePort.createBill(
                new BillingServicePort.BillingCreateCommand(
                        billNo, cmd.totalAmount(), "ENTERPRISE_SIGN_BILL",
                        "合同 #" + cmd.contractId() + " 签约账单"));

        // 5. 更新 billing_service_bill_id（stub 下为 null）
        repo.updateStatus(billId, "PENDING", billingResult.billingServiceBillId(),
                null, now);

        return new CreateBillResult(billId, billingResult.billingServiceBillId(),
                billingResult.paymentUrl());
    }

    @Transactional
    public void activateRoomSharedAccount(Long roomId, Long contractId) {
        // upsertRoomAccount 本身是幂等的（不存在则创建，存在则直接返回既有账户），
        // 这里不需要额外的存在性校验。
        repo.upsertRoomAccount(roomId, contractId, OffsetDateTime.now());
    }

    @Transactional
    public CreateBillResult createPersonalDepositBill(PersonalDepositBillCommand cmd) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 读取押金金额配置
        BigDecimal depositAmount = repo.getValue("personal_deposit_amount")
                .map(BigDecimal::new)
                .orElse(new BigDecimal("500.00"));

        // 2. 创建账单
        String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.DEPOSIT);
        Long billId = repo.insert(billNo, "PERSONAL_DEPOSIT_BILL",
                "TENANT", cmd.tenantId(),
                cmd.contractId(), cmd.roomId(), cmd.tenantId(),
                depositAmount, null, now);

        // 3. 创建个人押金台账 (PENDING_PAYMENT)
        repo.insert("PERSONAL", "TENANT", cmd.tenantId(),
                cmd.contractId(), cmd.stayId(), depositAmount,
                "PENDING_PAYMENT", billId, now);

        // 4. 调用 billing-service（stub）
        var billingResult = billingServicePort.createBill(
                new BillingServicePort.BillingCreateCommand(
                        billNo, depositAmount, "PERSONAL_DEPOSIT_BILL",
                        "租客 #" + cmd.tenantId() + " 入住押金"));

        repo.updateStatus(billId, "PENDING", billingResult.billingServiceBillId(), null, now);

        return new CreateBillResult(billId, billingResult.billingServiceBillId(),
                billingResult.paymentUrl());
    }

    @Transactional
    public void transferPersonalDepositEligibility(Long tenantId, Long fromStayId, Long toStayId) {
        DepositLedger ledger = repo.findPersonalByStay(tenantId, fromStayId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DEPOSIT_LEDGER_NOT_FOUND",
                        "未找到活跃押金台账 tenantId=" + tenantId + " stayId=" + fromStayId));

        if (!"ACTIVE".equals(ledger.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "DEPOSIT_NOT_ACTIVE",
                    "押金未激活，无法换宿：status=" + ledger.getStatus());
        }

        repo.updateCurrentStay(ledger.getId(), toStayId, OffsetDateTime.now());
    }

    @Transactional
    public TransferSubBalanceResult transferTenantSubBalance(Long tenantId,
                                                              Long fromRoomId,
                                                              Long toRoomId) {
        OffsetDateTime now = OffsetDateTime.now();

        RoomAccount fromAccount = repo.findByRoomId(fromRoomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "ROOM_ACCOUNT_NOT_FOUND", "原房间账户不存在 roomId=" + fromRoomId));
        RoomAccount toAccount = repo.upsertRoomAccount(toRoomId,
                fromAccount.getCurrentContractId(), now) != null
                ? repo.findByRoomId(toRoomId).orElseThrow()
                : repo.findByRoomId(toRoomId).orElseThrow();

        var fromSub = repo.find(fromAccount.getId(), "TENANT", tenantId);
        if (fromSub.isEmpty() || fromSub.get().getAvailableBalance()
                .compareTo(BigDecimal.ZERO) <= 0) {
            return new TransferSubBalanceResult(BigDecimal.ZERO);
        }

        RoomAccountSubBalance from = repo.findForUpdate(fromSub.get().getId()).orElseThrow();
        BigDecimal migration = from.getAvailableBalance();

        // 扣减原账户
        repo.updateBalance(from.getId(), BigDecimal.ZERO, from.getFrozenBalance(), now);
        repo.insert(fromAccount.getId(), from.getId(), "TENANT", tenantId,
                "TRANSFER_OUT", migration.negate(), null, now, "换宿迁移至房间 " + toRoomId);

        // 增加目标账户
        RoomAccountSubBalance toSub = repo.findOrCreate(toAccount.getId(), "TENANT", tenantId, now);
        RoomAccountSubBalance toSubLocked = repo.findForUpdate(toSub.getId()).orElseThrow();
        repo.updateBalance(toSubLocked.getId(),
                toSubLocked.getAvailableBalance().add(migration),
                toSubLocked.getFrozenBalance(), now);
        repo.insert(toAccount.getId(), toSubLocked.getId(), "TENANT", tenantId,
                "TRANSFER_IN", migration, null, now, "换宿从房间 " + fromRoomId + " 迁入");

        return new TransferSubBalanceResult(migration);
    }

    @Transactional
    public DepositSettlementResult settlePersonalDepositOnCheckout(Long tenantId, Long stayId) {
        OffsetDateTime now = OffsetDateTime.now();

        DepositLedger ledger = repo.findPersonalByStay(tenantId, stayId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DEPOSIT_LEDGER_NOT_FOUND", "押金台账不存在"));

        BigDecimal refundable = ledger.getOriginalAmount()
                .subtract(ledger.getOccupiedAmount() != null ? ledger.getOccupiedAmount() : BigDecimal.ZERO);

        // 更新押金台账 → REFUND_PENDING
        repo.updateStatus(ledger.getId(), "REFUND_PENDING", refundable, now);

        if (refundable.compareTo(BigDecimal.ZERO) > 0) {
            String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.REFUND);
            Long refundBillId = repo.insert(billNo, "REFUND_BILL",
                    "TENANT", tenantId, ledger.getContractId(), null, tenantId,
                    refundable, null, now);

            billingServicePort.createRefund(new BillingServicePort.BillingRefundCommand(
                    billNo, refundable, "退宿押金退还"));

            return new DepositSettlementResult(refundBillId, refundable);
        }

        return new DepositSettlementResult(null, BigDecimal.ZERO);
    }

    @Transactional
    public void settlePartialReturn(PartialReturnCommand cmd) {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long roomId : cmd.returnedRoomIds()) {
            settleRoomReturn(roomId, cmd.contractId(), now);
        }
    }

    @Transactional
    public void settleFullReturn(Long contractId) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 结算所有房间账户
        // （此处假设通过 contract 的 contractRoom 列表遍历，但 accounting 不直接查 contract 表；
        //   Room 列表由 contract 在调用 AccountingCommandPort 前传入 PartialReturnCommand；
        //   全量退房时 contract 会传入所有 ACTIVE 房间的 roomId 列表）
        // 注：由于 settleFullReturn 只接收 contractId，这里需查 room_account 表找所有相关账户
        List<RoomAccount> accounts = findActiveAccountsByContract(contractId);
        for (RoomAccount account : accounts) {
            settleRoomReturn(account.getRoomId(), contractId, now);
        }

        // 2. 结算企业押金
        List<DepositLedger> enterpriseDeposits =
                repo.findByContractId(contractId, "ENTERPRISE");
        for (DepositLedger d : enterpriseDeposits) {
            if ("ACTIVE".equals(d.getStatus())) {
                BigDecimal refundable = d.getOriginalAmount()
                        .subtract(d.getOccupiedAmount() != null
                                ? d.getOccupiedAmount() : BigDecimal.ZERO);
                repo.updateStatus(d.getId(), "REFUND_PENDING", refundable, now);

                if (refundable.compareTo(BigDecimal.ZERO) > 0) {
                    String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.REFUND);
                    repo.insert(billNo, "REFUND_BILL",
                            "ENTERPRISE", d.getOwnerId(), contractId, null, null,
                            refundable, null, now);
                    billingServicePort.createRefund(new BillingServicePort.BillingRefundCommand(
                            billNo, refundable, "合同退房企业押金退还"));
                }
            }
        }

        // 3. 回调 contract → SETTLING（结算账单支付后再回调 COMPLETED）
        contractCallbackPort.onSettlementCompleted(contractId);
    }

    private void settleRoomReturn(Long roomId, Long contractId, OffsetDateTime now) {
        repo.findByRoomId(roomId).ifPresent(account -> {
            // 退还剩余企业子余额
            List<RoomAccountSubBalance> subBalances =
                    repo.findAllByAccountId(account.getId());
            for (RoomAccountSubBalance sub : subBalances) {
                if (sub.getAvailableBalance().compareTo(BigDecimal.ZERO) > 0) {
                    String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.REFUND);
                    Long billId = repo.insert(billNo, "REFUND_BILL",
                            sub.getOwnerType(), sub.getOwnerId(),
                            contractId, roomId, null,
                            sub.getAvailableBalance(), null, now);
                    repo.updateBalance(sub.getId(), BigDecimal.ZERO,
                            sub.getFrozenBalance(), now);
                    repo.insert(account.getId(), sub.getId(), sub.getOwnerType(),
                            sub.getOwnerId(), "REFUND",
                            sub.getAvailableBalance().negate(), billId, now, "退房余额退还");
                    billingServicePort.createRefund(
                            new BillingServicePort.BillingRefundCommand(
                                    billNo, sub.getAvailableBalance(), "退房水电余额退还"));
                }
            }

            // 欠费追缴：汇总 INSUFFICIENT 流水，为每个欠费方生成 SETTLEMENT_BILL（spec 10.2）
            for (OwnerArrears arrears : repo.sumInsufficientByOwner(account.getId())) {
                if (arrears.amount().compareTo(BigDecimal.ZERO) <= 0) continue;
                String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.SETTLEMENT);
                repo.insert(billNo, "SETTLEMENT_BILL",
                        arrears.ownerType(), arrears.ownerId(), contractId, roomId,
                        "TENANT".equals(arrears.ownerType()) ? arrears.ownerId() : null,
                        arrears.amount(), null, now);
                billingServicePort.createBill(new BillingServicePort.BillingCreateCommand(
                        billNo, arrears.amount(), "SETTLEMENT_BILL",
                        "房间 #" + roomId + " 退房欠费追缴"));
            }

            repo.updateStatus(account.getId(), "CLOSED", now);
        });
    }

    @Transactional
    public DailyDeductionResult deductForDailySettlement(DailyDeductionCommand cmd) {
        OffsetDateTime now = OffsetDateTime.now();

        RoomAccount account = repo.findByRoomId(cmd.roomId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "ROOM_ACCOUNT_NOT_FOUND", "房间账户不存在 roomId=" + cmd.roomId()));

        // 1. 查企业子余额（取合同绑定的企业 ID，通过 account.currentContractId 间接关联）
        // 注：此处通过遍历子余额找 ENTERPRISE 类型
        BigDecimal enterpriseDeducted = BigDecimal.ZERO;
        BigDecimal tenantsDeducted = BigDecimal.ZERO;
        BigDecimal shortfall = BigDecimal.ZERO;

        List<RoomAccountSubBalance> subs = repo.findAllByAccountId(account.getId());

        // 企业子余额优先扣
        for (RoomAccountSubBalance sub : subs) {
            if (!"ENTERPRISE".equals(sub.getOwnerType())) continue;
            if (sub.getAvailableBalance().compareTo(BigDecimal.ZERO) <= 0) continue;

            RoomAccountSubBalance locked = repo.findForUpdate(sub.getId()).orElse(sub);
            BigDecimal toDeduct = cmd.enterpriseCoverAmount()
                    .min(locked.getAvailableBalance());
            if (toDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

            repo.updateBalance(locked.getId(),
                    locked.getAvailableBalance().subtract(toDeduct),
                    locked.getFrozenBalance(), now);
            repo.insert(account.getId(), locked.getId(), "ENTERPRISE", locked.getOwnerId(),
                    "DAILY_DEDUCT", toDeduct.negate(), null, now,
                    "日结扣款 " + cmd.settlementDate());
            enterpriseDeducted = enterpriseDeducted.add(toDeduct);
            break; // 企业只有一条子余额
        }

        // 租客子余额按分摊明细扣
        for (TenantDeductItem item : cmd.tenantApportionments()) {
            var subOpt = repo.find(account.getId(), "TENANT", item.tenantId());
            if (subOpt.isEmpty()) {
                shortfall = shortfall.add(item.amount());
                repo.insert(account.getId(), -1L, "TENANT", item.tenantId(),
                        "INSUFFICIENT", item.amount().negate(), null, now,
                        "欠费 " + cmd.settlementDate());
                continue;
            }
            RoomAccountSubBalance locked = repo.findForUpdate(subOpt.get().getId())
                    .orElse(subOpt.get());
            BigDecimal available = locked.getAvailableBalance();
            BigDecimal actualDeduct = item.amount().min(available);
            BigDecimal itemShortfall = item.amount().subtract(actualDeduct);

            if (actualDeduct.compareTo(BigDecimal.ZERO) > 0) {
                repo.updateBalance(locked.getId(),
                        available.subtract(actualDeduct),
                        locked.getFrozenBalance(), now);
                repo.insert(account.getId(), locked.getId(), "TENANT", item.tenantId(),
                        "DAILY_DEDUCT", actualDeduct.negate(), null, now,
                        "日结分摊扣款 " + cmd.settlementDate());
                tenantsDeducted = tenantsDeducted.add(actualDeduct);
            }
            if (itemShortfall.compareTo(BigDecimal.ZERO) > 0) {
                shortfall = shortfall.add(itemShortfall);
                repo.insert(account.getId(), locked.getId(), "TENANT", item.tenantId(),
                        "INSUFFICIENT", itemShortfall.negate(), null, now,
                        "欠费 " + cmd.settlementDate());
            }
        }

        return new DailyDeductionResult(enterpriseDeducted, tenantsDeducted, shortfall);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AccountingQueryPort 实现
    // ══════════════════════════════════════════════════════════════════════

    public BigDecimal getEnterpriseSubBalance(Long roomId, Long enterpriseId) {
        return repo.findByRoomId(roomId)
                .flatMap(acc -> repo.find(acc.getId(), "ENTERPRISE", enterpriseId))
                .map(RoomAccountSubBalance::getAvailableBalance)
                .orElse(BigDecimal.ZERO);
    }

    public DepositLedgerInfo getPersonalDepositLedger(Long tenantId, Long stayId) {
        return repo.findPersonalByStay(tenantId, stayId)
                .map(d -> new DepositLedgerInfo(
                        d.getId(), d.getStatus(),
                        d.getOriginalAmount(), d.getOccupiedAmount(), d.getRefundableAmount()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DEPOSIT_LEDGER_NOT_FOUND",
                        "押金台账不存在 tenantId=" + tenantId + " stayId=" + stayId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // billPaidCallback — billing-service 回调统一入口
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public void handleBillPaid(Long billingServiceBillId, BigDecimal actualAmount,
                               OffsetDateTime paidAt) {
        // 幂等：已经 PAID 则直接返回
        Bill bill = repo.findByBillingServiceBillId(billingServiceBillId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "BILL_NOT_FOUND", "账单不存在 billingServiceBillId=" + billingServiceBillId));

        if ("PAID".equals(bill.getBillStatus())) return;

        repo.updateStatus(bill.getId(), "PAID", billingServiceBillId, paidAt, OffsetDateTime.now());

        switch (bill.getBillType()) {
            case "ENTERPRISE_SIGN_BILL" -> {
                // 企业押金台账 → ACTIVE
                repo.findByOwnerAndContract("ENTERPRISE",
                        bill.getBillOwnerId(), bill.getContractId())
                        .ifPresent(d -> repo.updateStatus(d.getId(), "ACTIVE",
                                null, OffsetDateTime.now()));
                // 通知 contract → READY_FOR_CHECK_IN
                contractCallbackPort.onSignBillPaid(bill.getContractId(), bill.getId());
            }
            case "PERSONAL_DEPOSIT_BILL" -> {
                // 个人押金台账 → ACTIVE
                if (bill.getTenantId() != null) {
                    repo.findPersonalByStay(bill.getTenantId(), null)
                            .ifPresent(d -> repo.updateStatus(d.getId(), "ACTIVE",
                                    null, OffsetDateTime.now()));
                }
            }
            case "RECHARGE_BILL" -> {
                // 充值成功：增加对应子余额
                // 充值账单的 tenantId 存充值者，roomId 存目标房间
                if (bill.getRoomId() != null) {
                    repo.findByRoomId(bill.getRoomId()).ifPresent(account -> {
                        String ownerType = bill.getTenantId() != null ? "TENANT" : "ENTERPRISE";
                        Long ownerId = bill.getTenantId() != null
                                ? bill.getTenantId() : bill.getBillOwnerId();
                        RoomAccountSubBalance sub = repo.findOrCreate(
                                account.getId(), ownerType, ownerId, OffsetDateTime.now());
                        RoomAccountSubBalance locked = repo.findForUpdate(sub.getId()).orElse(sub);
                        repo.updateBalance(locked.getId(),
                                locked.getAvailableBalance().add(actualAmount),
                                locked.getFrozenBalance(), OffsetDateTime.now());
                        repo.insert(account.getId(), locked.getId(), ownerType, ownerId,
                                "RECHARGE", actualAmount, bill.getId(),
                                paidAt, "充值到账");
                    });
                }
            }
            case "SETTLEMENT_BILL" -> {
                // 欠费账单支付完成：标记欠费处理完成，回调 contract 完成结算
                // （markCompleted 内部有状态短路，settleFullReturn 已直接回调过时此处为幂等空操作）
                if (bill.getContractId() != null) {
                    contractCallbackPort.onSettlementCompleted(bill.getContractId());
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 对外 API 支撑（供 Delegate 调用）
    // ══════════════════════════════════════════════════════════════════════

    public RoomAccount getRoomAccountById(Long id) {
        return repo.findById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "ROOM_ACCOUNT_NOT_FOUND", "账户不存在：" + id));
    }

    public RoomAccount getRoomAccountByRoomId(Long roomId) {
        return repo.findByRoomId(roomId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "ROOM_ACCOUNT_NOT_FOUND", "房间账户不存在 roomId=" + roomId));
    }

    public List<RoomAccountSubBalance> getSubBalances(Long accountId) {
        return repo.findAllByAccountId(accountId);
    }

    /**
     * 发起充值：创建 RECHARGE_BILL 并调用 billing-service。
     */
    @Transactional
    public CreateBillResult initiateRecharge(Long roomId, Long roomAccountId,
                                              BigDecimal amount, String ownerType,
                                              Long ownerId) {
        OffsetDateTime now = OffsetDateTime.now();
        String billNo = billNoGenerator.generate(BillNoGenerator.Prefix.RECHARGE);

        Long tenantId = "TENANT".equals(ownerType) ? ownerId : null;
        Long billId = repo.insert(billNo, "RECHARGE_BILL",
                ownerType, ownerId, null, roomId, tenantId, amount, null, now);

        var billingResult = billingServicePort.createBill(
                new BillingServicePort.BillingCreateCommand(
                        billNo, amount, "RECHARGE_BILL", "房间 #" + roomId + " 水电充值"));
        repo.updateStatus(billId, "PENDING", billingResult.billingServiceBillId(), null, now);

        return new CreateBillResult(billId, billingResult.billingServiceBillId(),
                billingResult.paymentUrl());
    }

    private List<RoomAccount> findActiveAccountsByContract(Long contractId) {
        return repo.findActiveByContractId(contractId);
    }
}
