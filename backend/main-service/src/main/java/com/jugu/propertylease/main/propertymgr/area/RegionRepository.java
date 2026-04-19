package com.jugu.propertylease.main.propertymgr.area;

import com.jugu.propertylease.main.jooq.Tables;
import com.jugu.propertylease.main.jooq.tables.daos.RegionInfoDao;
import com.jugu.propertylease.main.jooq.tables.pojos.RegionInfo;
import com.jugu.propertylease.main.propertymgr.area.entity.RegionFilter;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@SuppressWarnings("ClassCanBeRecord")
@Repository
@RequiredArgsConstructor
public class RegionRepository {

  private final DSLContext dslContext;

  private final RegionInfoDao regionInfoDao;

  public void insert(RegionInfo regionInfo) {
    regionInfoDao.insert(regionInfo);
  }

  public RegionInfo findRegionById(String regionId) {
    return dslContext.selectFrom(Tables.REGION_INFO)
        .where(Tables.REGION_INFO.REGIONID.eq(regionId))
        .and(Tables.REGION_INFO.ISDELETED.eq(1))
        .fetchOneInto(RegionInfo.class);
  }

  public List<RegionInfo> findRegionsByIds(Collection<String> ids) {
    return dslContext.selectFrom(Tables.REGION_INFO)
        .where(Tables.REGION_INFO.REGIONID.in(ids))
        .and(Tables.REGION_INFO.ISDELETED.eq(1))
        .fetchInto(RegionInfo.class);
  }

  public List<RegionInfo> findRegionsByFilter(RegionFilter regionFilter) {
    Condition condition = buildCondition(regionFilter);
    return dslContext.selectFrom(Tables.REGION_INFO)
        .where(condition)
        .fetchInto(RegionInfo.class);
  }

  private Condition buildCondition(RegionFilter regionFilter) {
    Condition condition = DSL.noCondition();
    String des = regionFilter.getDesF();
    if (StringUtils.hasText(des)) {
      condition = condition.and(Tables.REGION_INFO.REGIONDES.contains(des));
    }

    String id = regionFilter.getIdF();
    if (StringUtils.hasText(id)) {
      condition = condition.and(Tables.REGION_INFO.REGIONID.contains(id));
    }

    return condition;
  }

  public int updateRegionDes(String des, String regionId, String userId, String userName,
      String time) {
    return dslContext.update(Tables.REGION_INFO)
        .set(Tables.REGION_INFO.REGIONDES, des)
        .set(Tables.REGION_INFO.UPDATEUSERID, userId)
        .set(Tables.REGION_INFO.UPDATEUSERNAME, userName)
        .set(Tables.REGION_INFO.UPDATETIME, time)
        .where(Tables.REGION_INFO.REGIONID.eq(regionId))
        .execute();
  }

  public int countById(String regionId) {
    Integer count = dslContext.selectCount()
        .from(Tables.REGION_INFO)
        .where(Tables.REGION_INFO.REGIONID.eq(regionId))
        .and(Tables.REGION_INFO.ISDELETED.eq(1))
        .fetchOneInto(Integer.class);
    return count == null ? 0 : count;
  }
}
