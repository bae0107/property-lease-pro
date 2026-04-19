package com.jugu.propertylease.main.propertymgr.store;

import com.jugu.propertylease.main.jooq.Tables;
import com.jugu.propertylease.main.jooq.tables.daos.StoreInfoDao;
import com.jugu.propertylease.main.jooq.tables.pojos.StoreInfo;
import com.jugu.propertylease.main.propertymgr.store.entity.StoreFilter;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class StoreRepository {

  private final DSLContext dslContext;

  private final StoreInfoDao storeInfoDao;

  public void createStore(StoreInfo storeInfo) {
    storeInfoDao.insert(storeInfo);
  }

  public StoreInfo findStoreById(long id) {
    return dslContext.selectFrom(Tables.STORE_INFO)
        .where(Tables.STORE_INFO.STOREID.eq(id))
        .fetchOneInto(StoreInfo.class);
  }

  public List<StoreInfo> findStoresByFilter(StoreFilter storeFilter) {
    Condition condition = buildCondition(storeFilter);
    return dslContext.selectFrom(Tables.STORE_INFO)
        .where(condition)
        .fetchInto(StoreInfo.class);
  }

  private Condition buildCondition(StoreFilter storeFilter) {
    Condition condition = DSL.noCondition();
    String name = storeFilter.getNameF();
    if (StringUtils.hasText(name)) {
      condition = condition.and(Tables.STORE_INFO.STORENAME.contains(name));
    }

    return condition;
  }

  public int countById(long storeId) {
    Integer count = dslContext.selectCount()
        .from(Tables.STORE_INFO)
        .where(Tables.STORE_INFO.STOREID.eq(storeId))
        .and(Tables.STORE_INFO.ISDELETED.eq(1))
        .fetchOneInto(Integer.class);
    return count == null ? 0 : count;
  }

  public List<StoreInfo> findStoresByIds(Collection<Long> ids) {
    return dslContext.selectFrom(Tables.STORE_INFO)
        .where(Tables.STORE_INFO.STOREID.in(ids))
        .and(Tables.STORE_INFO.ISDELETED.eq(1))
        .fetchInto(StoreInfo.class);
  }

  public void update(StoreInfo storeInfo) {
    storeInfoDao.update(storeInfo);
  }
}
