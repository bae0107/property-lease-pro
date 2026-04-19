package com.jugu.propertylease.main.propertymgr;

import com.jugu.propertylease.main.MainServiceApplication;
import com.jugu.propertylease.main.propertymgr.area.AdminRegions;
import com.jugu.propertylease.main.propertymgr.area.CreateRegionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MainServiceApplication.class)
public class PropertyMgrTests {

  @Autowired
  AdminRegions adminRegions;

  @Test
  public void getRegionMapTest() {
    List<AdminRegions.Province> provinces = adminRegions.getProvinces();
    System.out.println(provinces);
  }

  @Test
  public void getRegionIdTest() {
    CreateRegionRequest regionRequest = new CreateRegionRequest();
    regionRequest.setProvince("河北省");
    regionRequest.setProvinceCode("005");
    regionRequest.setCity("石家庄市");
    regionRequest.setCityCode("001");
//        regionRequest.setDistrict("东城区");
//        regionRequest.setDistrictCode("001");
//        System.out.println(adminRegions.genRegionId(regionRequest));
  }
}
