package com.jugu.propertylease.billing.app.repository;

import com.jugu.propertylease.billing.app.jooq.Tables;
import com.jugu.propertylease.billing.app.jooq.tables.daos.BillingRecordInfoDao;
import com.jugu.propertylease.billing.app.jooq.tables.pojos.BillingRecordInfo;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;

@SuppressWarnings("ClassCanBeRecord")
@Repository
@RequiredArgsConstructor
public class BillingRecordInfoRepository {

  private final DSLContext dslContext;

  private final BillingRecordInfoDao billingRecordInfoDao;

  public int countSize() {
    return dslContext.selectCount()
        .from(Tables.BILLING_RECORD_INFO)
        .fetchOptional()
        .map(Record1::value1)
        .orElse(0);
  }

  public BillingRecordInfo findBillForUpdate(String billingId) {
    return dslContext.selectFrom(Tables.BILLING_RECORD_INFO)
        .where(Tables.BILLING_RECORD_INFO.BILLINGID.eq(billingId))
        .forUpdate()
        .fetchOneInto(BillingRecordInfo.class);
  }

  public BillingRecordInfo findBillById(String billingId) {
    return dslContext.selectFrom(Tables.BILLING_RECORD_INFO)
        .where(Tables.BILLING_RECORD_INFO.BILLINGID.eq(billingId))
        .fetchOneInto(BillingRecordInfo.class);
  }

  public List<BillingRecordInfo> finBillsByIds(Collection<String> ids) {
    return dslContext.selectFrom(Tables.BILLING_RECORD_INFO)
        .where(Tables.BILLING_RECORD_INFO.BILLINGID.in(ids))
        .fetchInto(BillingRecordInfo.class);
  }

  public String findStatusById(String id) {
    return dslContext.select(Tables.BILLING_RECORD_INFO.BILLINGSTATUS)
        .from(Tables.BILLING_RECORD_INFO)
        .where(Tables.BILLING_RECORD_INFO.BILLINGID.eq(id))
        .fetchOneInto(String.class);
  }

  public List<BillingRecordInfo> findBillingsByStatus(String status) {
    return dslContext.selectFrom(Tables.BILLING_RECORD_INFO)
        .where(Tables.BILLING_RECORD_INFO.BILLINGSTATUS.eq(status))
        .fetchInto(BillingRecordInfo.class);
  }


  public void update(BillingRecordInfo recordInfo) {
    billingRecordInfoDao.update(recordInfo);
  }

  public void insert(BillingRecordInfo recordInfo) {
    billingRecordInfoDao.insert(recordInfo);
  }
}
