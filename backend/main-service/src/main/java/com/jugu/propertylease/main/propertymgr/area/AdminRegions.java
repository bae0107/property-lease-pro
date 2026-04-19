package com.jugu.propertylease.main.propertymgr.area;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.jooq.tables.pojos.RegionInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.InputStream;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class AdminRegions {

  private final China china;

  public AdminRegions(@Value("${region.file.path}") String regionPath,
      ResourceLoader resourceLoader) {
    try {
      Resource resource = resourceLoader.getResource(regionPath);
      InputStream xmlStream = resource.getInputStream();
      JAXBContext context = JAXBContext.newInstance(China.class);
      Unmarshaller unmarshaller = context.createUnmarshaller();
      this.china = (China) unmarshaller.unmarshal(xmlStream);
    } catch (Exception e) {
      throw new RuntimeException("fail to load regions", e);
    }
  }

  public List<Province> getProvinces() {
    return List.copyOf(china.getProvinces());
  }

  public void parseRegion(CreateRegionRequest createRegionRequest, RegionInfo regionInfo) {
    if (!createRegionRequest.isLegal()) {
      throw new IllegalArgumentException("请求缺少必填项值");
    }
    String prCode = createRegionRequest.getProvinceCode();
    List<Province> provinces = getProvinces();
    String pro = createRegionRequest.getProvince();
    int pIndex = provinces.indexOf(new Province(pro, prCode));
    if (pIndex == -1) {
      throw new IllegalArgumentException("请求省/直辖市信息错误");
    }
    regionInfo.setProvince(pro);
    Province province = provinces.get(pIndex);
    String cityCode = createRegionRequest.getCityCode();
    List<City> cities = province.getCities();
    String cit = createRegionRequest.getCity();
    int cIndex = cities.indexOf(new City(cit, cityCode));
    if (cIndex == -1) {
      throw new IllegalArgumentException("请求地级市/直辖市信息错误");
    }
    regionInfo.setCity(cit);
    City city = cities.get(cIndex);
    List<District> districts = city.getDistricts();
    if (Common.isCollectionInValid(districts)) {
      regionInfo.setRegionid(prCode + "-" + cityCode + "-000");
      return;
    }
    String dis = createRegionRequest.getDistrict();
    String disCode = createRegionRequest.getDistrictCode();
    if (Common.isStringInValid(dis) || Common.isStringInValid(disCode)) {
      throw new IllegalArgumentException("直辖市区域需填写行政区信息");
    }
    int dIndex = districts.indexOf(new District(dis, disCode));
    if (dIndex == -1) {
      throw new IllegalArgumentException("请求行政区信息错误");
    }
    regionInfo.setDistrict(dis);
    regionInfo.setRegionid(prCode + "-" + cityCode + "-" + disCode);
  }

  @Data
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @XmlAccessorType(XmlAccessType.FIELD)
  @Schema(description = "行政区")
  @NoArgsConstructor
  public static class District {

    @XmlAttribute(name = "name")
    @Schema(description = "名称")
    @EqualsAndHashCode.Include
    private String name;

    @XmlAttribute(name = "code")
    @Schema(description = "编号")
    @EqualsAndHashCode.Include
    private String code;

    public District(String name, String code) {
      this.name = name;
      this.code = code;
    }
  }

  @Data
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @XmlAccessorType(XmlAccessType.FIELD)
  @Schema(description = "行政市")
  @NoArgsConstructor
  public static class City {

    @XmlAttribute(name = "name")
    @Schema(description = "名称")
    @EqualsAndHashCode.Include
    private String name;

    @XmlAttribute(name = "code")
    @EqualsAndHashCode.Include
    @Schema(description = "编号")
    private String code;

    @XmlElement(name = "district")
    private List<District> districts;

    public City(String name, String code) {
      this.name = name;
      this.code = code;
    }
  }

  @Data
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @XmlAccessorType(XmlAccessType.FIELD)
  @Schema(description = "行政省")
  @NoArgsConstructor
  public static class Province {

    @XmlAttribute(name = "name")
    @Schema(description = "名称")
    @EqualsAndHashCode.Include
    private String name;

    @XmlAttribute(name = "code")
    @EqualsAndHashCode.Include
    @Schema(description = "编号")
    private String code;

    @XmlElement(name = "city")
    private List<City> cities;

    public Province(String name, String code) {
      this.name = name;
      this.code = code;
    }
  }

  @Data
  @XmlRootElement(name = "china")
  @XmlAccessorType(XmlAccessType.FIELD)
  @NoArgsConstructor
  public static class China {

    @XmlElement(name = "province")
    private List<Province> provinces;
  }
}
