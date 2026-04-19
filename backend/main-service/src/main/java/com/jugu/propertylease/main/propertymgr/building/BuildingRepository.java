package com.jugu.propertylease.main.propertymgr.building;

import com.jugu.propertylease.main.jooq.Tables;
import com.jugu.propertylease.main.jooq.tables.daos.BuildingInfoDao;
import com.jugu.propertylease.main.jooq.tables.pojos.BuildingInfo;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BuildingRepository {

  private final DSLContext dslContext;

  private final BuildingInfoDao buildingInfoDao;

  public void addNewBuilding(BuildingInfo buildingInfo) {
    buildingInfoDao.insert(buildingInfo);
  }

  public BuildingInfo findBuildingById(String id) {
    return dslContext.selectFrom(Tables.BUILDING_INFO)
        .where(Tables.BUILDING_INFO.BUILDINGID.eq(id))
        .fetchOneInto(BuildingInfo.class);
  }

  public int countById(String buildingId) {
    Integer count = dslContext.selectCount()
        .from(Tables.BUILDING_INFO)
        .where(Tables.BUILDING_INFO.BUILDINGID.eq(buildingId))
        .and(Tables.BUILDING_INFO.ISDELETED.eq(1))
        .fetchOneInto(Integer.class);
    return count == null ? 0 : count;
  }

  public int countByStore(long storeId) {
    Integer count = dslContext.selectCount()
        .from(Tables.BUILDING_INFO)
        .where(Tables.BUILDING_INFO.STOREID.eq(storeId))
        .fetchOneInto(Integer.class);
    return count == null ? 0 : count;
  }

  public void update(BuildingInfo buildingInfo) {
    buildingInfoDao.update(buildingInfo);
  }
}
