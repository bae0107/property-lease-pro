package com.jugu.propertylease.main.contract.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.model.EnterpriseSignBillCommand;
import com.jugu.propertylease.main.accounting.api.model.PartialReturnCommand;
import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.customer.api.CustomerQueryPort;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractChargeRule;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import com.jugu.propertylease.main.occupancy.api.OccupancyQueryPort;
import com.jugu.propertylease.main.propertymgr.api.AssetCommandPort;
import com.jugu.propertylease.main.propertymgr.api.AssetQueryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 合同生命周期核心编排服务。
 *
 * <p>状态机：
 * <pre>
 * DRAFT ──confirmContract──▶ SIGN_BILL_PENDING ──(onSignBillPaid 回调)──▶ READY_FOR_CHECK_IN
 *   │                              │                                         │
 *   └──────────cancel──────────────┘                                 applyPartialReturn
 *                                                                             │
 *                                                                   PARTIALLY_RETURNED
 *                                                                             │
 *                                                                     applyFullReturn
 *                                                                             ▼
 *                                                                         SETTLING
 *                                                                             │(onSettlementCompleted 回调)
 *                                                                             ▼
 *                                                                        COMPLETED
 * </pre>
 *
 * <p>⚠️ 本文件是本轮重建：{@code ContractLifecycleService.java} 在上传包里完全缺失。
 * 状态机与编排步骤依据 HANDOFF.md 里的方法清单 + {@code ContractPorts.java} 接口 Javadoc
 * 中已经写明的状态集合设计，并与用户逐条确认过后落地（企业押金金额来自 charge_rule；
 * ROOM_SHARED 账户激活方法为本轮新增；cancel 只允许 DRAFT/SIGN_BILL_PENDING）。
 */
@Service
public class ContractLifecycleService {

    // ── 状态常量 ────────────────────────────────────────────────────────────
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SIGN_BILL_PENDING = "SIGN_BILL_PENDING";
    public static final String STATUS_READY_FOR_CHECK_IN = "READY_FOR_CHECK_IN";
    public static final String STATUS_PARTIALLY_RETURNED = "PARTIALLY_RETURNED";
    public static final String STATUS_SETTLING = "SETTLING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final String ROOM_STATUS_PENDING = "PENDING";
    private static final String ROOM_STATUS_ACTIVE = "ACTIVE";
    private static final String ROOM_STATUS_RETURNED = "RETURNED";

    /** 企业押金在 charge_rule 中的类型标记（用户确认：企业押金金额来自 charge_rule，非 system_config）。*/
    private static final String CHARGE_TYPE_ENTERPRISE_DEPOSIT = "ENTERPRISE_DEPOSIT";

    private final ContractRepository repo;
    private final ContractNoGenerator contractNoGenerator;
    private final CustomerQueryPort customerQueryPort;
    private final AssetQueryPort assetQueryPort;
    private final AssetCommandPort assetCommandPort;
    private final AccountingCommandPort accountingCommandPort;
    private final OccupancyQueryPort occupancyQueryPort;

    public ContractLifecycleService(ContractRepository repo,
                                     ContractNoGenerator contractNoGenerator,
                                     CustomerQueryPort customerQueryPort,
                                     AssetQueryPort assetQueryPort,
                                     AssetCommandPort assetCommandPort,
                                     AccountingCommandPort accountingCommandPort,
                                     OccupancyQueryPort occupancyQueryPort) {
        this.repo = repo;
        this.contractNoGenerator = contractNoGenerator;
        this.customerQueryPort = customerQueryPort;
        this.assetQueryPort = assetQueryPort;
        this.assetCommandPort = assetCommandPort;
        this.accountingCommandPort = accountingCommandPort;
        this.occupancyQueryPort = occupancyQueryPort;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. createDraft
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public ContractDetail createDraft(CreateDraftCommand cmd) {
        // 企业必须存在（getEnterprise 内部找不到会抛 404）
        customerQueryPort.getEnterprise(cmd.enterpriseId());

        if (cmd.rooms() == null || cmd.rooms().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "CONTRACT_ROOMS_REQUIRED", "合同至少需要一个房间");
        }

        // 草稿阶段只做可用性校验，不加锁（真正加锁在 confirmContract）
        for (CreateContractRoomCommand room : cmd.rooms()) {
            if (!assetQueryPort.isRoomAvailableForContract(room.roomId())
                    || repo.existsActiveContractRoom(room.roomId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ROOM_NOT_AVAILABLE", "房间不可用，已被其它合同占用 roomId=" + room.roomId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        String contractNo = contractNoGenerator.generate();

        Long contractId = repo.insertContract(contractNo, cmd.enterpriseId(), STATUS_DRAFT,
                cmd.startDate(), cmd.endDate(), cmd.paymentMode(), cmd.remark(),
                cmd.operatorId(), now);

        for (CreateContractRoomCommand room : cmd.rooms()) {
            repo.insertContractRoom(contractId, room.roomId(), room.signedRent(),
                    room.leaseStart(), room.leaseEnd(), now);
        }

        if (cmd.chargeRules() != null) {
            for (CreateChargeRuleCommand rule : cmd.chargeRules()) {
                repo.insertChargeRule(contractId, rule.chargeType(), rule.payerType(),
                        rule.amount(), rule.ruleSnapshot(), now);
            }
        }

        return loadDetail(contractId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. confirmContract
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public ContractDetail confirmContract(Long contractId, Long operatorId) {
        Contract contract = requireContract(contractId);
        requireStatus(contract, STATUS_DRAFT, "仅 DRAFT 状态可确认签约");

        List<ContractRoom> rooms = repo.findRoomsByContract(contractId, null);
        if (rooms.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_ROOMS_REQUIRED", "合同下没有房间，无法确认");
        }

        // 草稿阶段没有加锁，这里是加锁前最后一次可用性校验
        for (ContractRoom room : rooms) {
            if (!assetQueryPort.isRoomAvailableForContract(room.getRoomId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ROOM_NOT_AVAILABLE",
                        "房间已不可用，请重新选房 roomId=" + room.getRoomId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();

        BigDecimal rentAmount = BigDecimal.ZERO;
        for (ContractRoom room : rooms) {
            assetCommandPort.lockRoom(room.getRoomId(), contractId);
            repo.updateRoomStatus(room.getId(), ROOM_STATUS_ACTIVE, null, now);
            rentAmount = rentAmount.add(room.getSignedRent());
        }

        BigDecimal depositAmount = repo.findChargeRules(contractId).stream()
                .filter(r -> CHARGE_TYPE_ENTERPRISE_DEPOSIT.equals(r.getChargeType()))
                .map(ContractChargeRule::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = rentAmount.add(depositAmount);

        var billResult = accountingCommandPort.createEnterpriseSignBill(
                new EnterpriseSignBillCommand(contractId, contract.getEnterpriseId(),
                        totalAmount, depositAmount));

        repo.updateSignBillId(contractId, billResult.billId(), now);
        repo.updateStatus(contractId, STATUS_SIGN_BILL_PENDING, now);

        return loadDetail(contractId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. cancel
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public void cancel(Long contractId, Long operatorId) {
        Contract contract = requireContract(contractId);

        if (!STATUS_DRAFT.equals(contract.getStatus())
                && !STATUS_SIGN_BILL_PENDING.equals(contract.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_STATUS_INVALID",
                    "仅 DRAFT / SIGN_BILL_PENDING 状态可取消，当前：" + contract.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ContractRoom> rooms = repo.findRoomsByContract(contractId, ROOM_STATUS_ACTIVE);
        for (ContractRoom room : rooms) {
            assetCommandPort.releaseRoom(room.getRoomId(), contractId);
            repo.updateRoomStatus(room.getId(), ROOM_STATUS_RETURNED, now, now);
        }

        repo.updateStatus(contractId, STATUS_CANCELLED, now);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. applyPartialReturn
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public void applyPartialReturn(Long contractId, List<Long> contractRoomIds, Long operatorId) {
        Contract contract = requireContract(contractId);
        requireStatus(contract, STATUS_READY_FOR_CHECK_IN,
                "仅 READY_FOR_CHECK_IN 状态可部分退房（PARTIALLY_RETURNED 状态请走 applyFullReturn 结束合同）");

        if (contractRoomIds == null || contractRoomIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "ROOM_IDS_REQUIRED", "退房房间列表不能为空");
        }

        List<ContractRoom> targets = contractRoomIds.stream()
                .map(id -> repo.findRoomById(id)
                        .filter(r -> r.getContractId().equals(contractId))
                        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                                "CONTRACT_ROOM_NOT_FOUND", "合同房间不存在或不属于该合同：" + id)))
                .toList();

        for (ContractRoom room : targets) {
            if (!ROOM_STATUS_ACTIVE.equals(room.getStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "CONTRACT_ROOM_STATUS_INVALID",
                        "仅 ACTIVE 状态房间可退房 contractRoomId=" + room.getId());
            }
            if (occupancyQueryPort.hasCheckedInStays(room.getRoomId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ROOM_HAS_CHECKED_IN_TENANT",
                        "房间仍有在住租客，需先办理退宿 roomId=" + room.getRoomId());
            }
        }

        accountingCommandPort.settlePartialReturn(new PartialReturnCommand(contractId,
                targets.stream().map(ContractRoom::getRoomId).toList()));

        OffsetDateTime now = OffsetDateTime.now();
        for (ContractRoom room : targets) {
            assetCommandPort.releaseRoom(room.getRoomId(), contractId);
            repo.updateRoomStatus(room.getId(), ROOM_STATUS_RETURNED, now, now);
        }

        repo.updateStatus(contractId, STATUS_PARTIALLY_RETURNED, now);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. applyFullReturn
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public void applyFullReturn(Long contractId, Long operatorId) {
        Contract contract = requireContract(contractId);

        if (!STATUS_READY_FOR_CHECK_IN.equals(contract.getStatus())
                && !STATUS_PARTIALLY_RETURNED.equals(contract.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_STATUS_INVALID",
                    "仅 READY_FOR_CHECK_IN / PARTIALLY_RETURNED 状态可整体退房，当前："
                            + contract.getStatus());
        }

        List<ContractRoom> activeRooms = repo.findRoomsByContract(contractId, ROOM_STATUS_ACTIVE);
        for (ContractRoom room : activeRooms) {
            if (occupancyQueryPort.hasCheckedInStays(room.getRoomId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ROOM_HAS_CHECKED_IN_TENANT",
                        "房间仍有在住租客，需先全部办理退宿 roomId=" + room.getRoomId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        repo.updateStatus(contractId, STATUS_SETTLING, now);

        accountingCommandPort.settleFullReturn(contractId);

        for (ContractRoom room : activeRooms) {
            assetCommandPort.releaseRoom(room.getRoomId(), contractId);
            repo.updateRoomStatus(room.getId(), ROOM_STATUS_RETURNED, now, now);
        }

        // settleFullReturn 目前是同步 stub 实现，直接视为完成；
        // 若后续接入真实 billing-service 异步结算，应改为等待
        // ContractCallbackPort.onSettlementCompleted 回调再流转到 COMPLETED，
        // 届时这一行需要删除，只保留 SETTLING。
        repo.updateStatus(contractId, STATUS_COMPLETED, now);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 供 ContractCallbackPortImpl 复用的内部状态流转
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public void markReadyForCheckIn(Long contractId) {
        Contract contract = requireContract(contractId);

        // 幂等
        if (STATUS_READY_FOR_CHECK_IN.equals(contract.getStatus())) {
            return;
        }
        requireStatus(contract, STATUS_SIGN_BILL_PENDING,
                "仅 SIGN_BILL_PENDING 状态可推进为 READY_FOR_CHECK_IN");

        OffsetDateTime now = OffsetDateTime.now();
        for (ContractRoom room : repo.findRoomsByContract(contractId, ROOM_STATUS_ACTIVE)) {
            accountingCommandPort.activateRoomSharedAccount(room.getRoomId(), contractId);
        }
        repo.updateStatus(contractId, STATUS_READY_FOR_CHECK_IN, now);
    }

    @Transactional
    public void markCompleted(Long contractId) {
        Contract contract = requireContract(contractId);

        // 幂等
        if (STATUS_COMPLETED.equals(contract.getStatus())) {
            return;
        }
        requireStatus(contract, STATUS_SETTLING, "仅 SETTLING 状态可推进为 COMPLETED");
        repo.updateStatus(contractId, STATUS_COMPLETED, OffsetDateTime.now());
    }

    // ══════════════════════════════════════════════════════════════════════
    // helpers
    // ══════════════════════════════════════════════════════════════════════

    private Contract requireContract(Long contractId) {
        return repo.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "CONTRACT_NOT_FOUND", "合同不存在：" + contractId));
    }

    private void requireStatus(Contract contract, String expected, String message) {
        if (!expected.equals(contract.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_STATUS_INVALID", message + "，当前：" + contract.getStatus());
        }
    }

    private ContractDetail loadDetail(Long contractId) {
        Contract contract = requireContract(contractId);
        List<ContractRoom> rooms = repo.findRoomsByContract(contractId, null);
        List<ContractChargeRule> rules = repo.findChargeRules(contractId);
        return new ContractDetail(contract, rooms, rules);
    }
}
