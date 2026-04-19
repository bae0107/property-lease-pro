package com.jugu.propertylease.common.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;

public class Common {

  public static boolean isStringInValid(String str) {
    return str == null || str.length() == 0;
  }

  public static <E> boolean isCollectionInValid(Collection<E> collection) {
    return collection == null || collection.isEmpty();
  }

  public static Optional<String> findSysTimeAfter(String time, int period, int scale) {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    try {
      calendar.setTime(formatter.parse(time));
      calendar.add(scale, period);
      return Optional.of(formatter.format(calendar.getTime()));
    } catch (ParseException e) {
      return Optional.empty();
    }
  }

  public static String findSysTime() {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return formatter.format(calendar.getTime());
  }

  public static String findDatSysTime() {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    return formatter.format(calendar.getTime());
  }

  public static String findWxFormatTime() {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
    return formatter.format(calendar.getTime());
  }

  public static Optional<String> findSysTimeAfter(String time, int min) {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    try {
      calendar.setTime(formatter.parse(time));
      calendar.add(Calendar.MINUTE, min);
      return Optional.of(formatter.format(calendar.getTime()));
    } catch (ParseException e) {
      return Optional.empty();
    }
  }

  public static String findTimeBySecondTimestamp(int seconds) {
    long mill = Long.parseLong(String.valueOf(seconds)) * 1000L;
    return findTimeByMillSecondTimestamp(mill);
  }

  public static String findTimeSecondByUTC(String utcTime) {
    return java.time.Instant.parse(utcTime)
        .atZone(java.time.ZoneId.of("UTC"))
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }

  public static String findTimeSecondByISO(String isoTime) {
    return OffsetDateTime.parse(isoTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
        .withOffsetSameInstant(ZoneOffset.ofHours(8))
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }

  public static String findTimeByMillSecondTimestamp(long millSeconds) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Date date = new Date(millSeconds);
    return sdf.format(date);
  }
}
