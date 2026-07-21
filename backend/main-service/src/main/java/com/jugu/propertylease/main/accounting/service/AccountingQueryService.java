package com.jugu.propertylease.main.accounting.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.repo.jooq.JooqAccountingRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.Bill;
import com.jugu.propertylease.main.jooq.tables.pojos.DepositLedger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AccountingService 的查询扩展方法（供 Delegate 调用）。
 *
 * <p>与 AccountingService 共享同一个 Service Bean 实现，
 * 此文件仅作为额外方法的补充说明，实际应直接合并到 AccountingService.java 中。
 *
 * <p>合并时将以下方法加入 AccountingService 类体：
 * <ul>
 *   <li>getBillById</li>
 *   <li>cancelBill</li>
 *   <li>findBills / countBills</li>
 *   <li>findDepositLedgers / countDepositLedgers / getDepositLedgerById</li>
 *   <li>getEntries / countEntries</li>
 * </ul>
 */
@Service
public class AccountingQueryService {

    private final JooqAccountingRepository repo;

    public AccountingQueryService(JooqAccountingRepository repo) {
        this.repo = repo;
    }

    // ── Bill ────────────────────────────────────────────────────────────────

    public Bill getBillById(Long id) {
        return repo.findBillById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "账单不存在：" + id));
    }

    public void cancelBill(Long id) {
        Bill bill = getBillById(id);
        if (!"PENDING".equals(bill.getBillStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "BILL_STATUS_INVALID", "仅 PENDING 状态的账单可作废，当前：" + bill.getBillStatus());
        }
        repo.cancel(id, OffsetDateTime.now());
    }

    public List<Bill> findBills(String ownerType, Long ownerId, String status,
                                 String billType, LocalDate start, LocalDate end,
                                 int offset, int limit) {
        return repo.findByOwner(ownerType, ownerId, status, billType, start, end, offset, limit);
    }

    public int countBills(String ownerType, Long ownerId, String status,
                           String billType, LocalDate start, LocalDate end) {
        return repo.countByOwner(ownerType, ownerId, status, billType, start, end);
    }

    // ── DepositLedger ────────────────────────────────────────────────────────

    public DepositLedger getDepositLedgerById(Long id) {
        return repo.findLedgerById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "DEPOSIT_LEDGER_NOT_FOUND", "押金台账不存在：" + id));
    }

    public List<DepositLedger> findDepositLedgers(String depositType, Long ownerId,
                                                    String status, int offset, int limit) {
        return repo.findAll(depositType, ownerId, status, offset, limit);
    }

    public int countDepositLedgers(String depositType, Long ownerId, String status) {
        return repo.countAll(depositType, ownerId, status);
    }

    // ── RoomAccountEntry ─────────────────────────────────────────────────────

    public List<?> getEntries(Long accountId, String ownerType, Long ownerId,
                               String entryType, LocalDate startDate, LocalDate endDate,
                               int offset, int limit) {
        return repo.findByAccountId(accountId, ownerType, ownerId,
                entryType, startDate, endDate, offset, limit);
    }

    public int countEntries(Long accountId, String ownerType, Long ownerId,
                             String entryType, LocalDate startDate, LocalDate endDate) {
        return repo.countByAccountId(accountId, ownerType, ownerId,
                entryType, startDate, endDate);
    }
}
