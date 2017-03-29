package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.Test;

import delays.java.stream.pojos.StationBoardEntryAnalytics;
import delays.java.stream.pojos.StopAnalytics;
import delays.java.stream.pojos.TrainAnalytics;

public class EmbeddedAnalyticsTest {

   @Test
   public void testDateParsingNoTimezone() throws ParseException {
      String date = "Fri Jan 08 2016 17:20:04";
      DateFormat df = new SimpleDateFormat("EEE MMM dd yyyy kk:mm:ss", Locale.ENGLISH);
      Date result =  df.parse(date);
      System.out.println(result);
   }

   @Test
   public void testDateParsingBasicTimezone() throws ParseException {
      String date = "Fri Jan 08 2016 17:20:04 (CET)";
      DateFormat df = new SimpleDateFormat("EEE MMM dd yyyy kk:mm:ss (zzz)", Locale.ENGLISH);
      Date result =  df.parse(date);
      System.out.println(result);
   }

   @Test
   public void testDateParsingGMTOffsetTimezone() throws ParseException {
      String date = "Fri Jan 08 2016 17:20:04 GMT+0100 (CET)";
      Date result = parseTimemstamp(date);
      System.out.println(result);
   }

   private Date parseTimemstamp(String date) {
      DateFormat df = new SimpleDateFormat("EEE MMM dd yyyy kk:mm:ss 'GMT'Z", Locale.ENGLISH);
      try {
         return df.parse(date);
      } catch (ParseException e) {
         throw new AssertionError(e);
      }
   }

   @Test
   public void testEmbeddedAnalytics() throws IOException {
      EmbeddedCacheManager manager = new DefaultCacheManager();
      manager.defineConfiguration("local", new ConfigurationBuilder().build());
      Cache<String, StationBoardEntryAnalytics> cache = manager.getCache("local");

      final String file = "src/test/resources/station-boards-dump_2000.tsv";
      try (Stream<String> lines = Files.lines(Paths.get(file))) {
         lines.skip(1) // Skip header
            .forEach(l -> {
               String[] parts = l.split("\t");

               String id = parts[0];
               Date entryTs = parseTimemstamp(parts[1]);
               long stopId = Long.parseLong(parts[2]);
               String stopName = parts[3];
               Date departureTs = parseTimemstamp(parts[4]);
               String trainName = parts[5];
               String trainCat = parts[6];
               String trainOperator = parts[7];
               String trainTo = parts[8];
               int delayMin = parts[9].isEmpty() ? 0 : Integer.parseInt(parts[9]);
               String capacity1st = parts[10];
               String capacity2nd = parts[11];

               TrainAnalytics train = new TrainAnalytics(trainName, trainTo, trainCat, trainOperator);
               StopAnalytics stop = new StopAnalytics(stopId, stopName);
               StationBoardEntryAnalytics entry = new StationBoardEntryAnalytics(
                  train, departureTs, null, delayMin, null, stop, entryTs, capacity1st, capacity2nd);

//               StationBoardEntryAnalytics prev = cache.get(id);
//               if (prev != null) {
//                  System.out.println("Prev   : " + prev);
//                  System.out.println("Current: " + entry);
//               }

               // By keeping data per id, duplicates for each id are avoided
               // Since data is kept in order of capturing, it does not mean
               // that the last id is the latest train position in time...
               // A more correct version would pick the last entry for a
               // particular train id in terms of the last stop.
               cache.put(id, entry);
         });
      }

      int totalStationBoards = cache.size();

      Map<Integer, Long> totalPerHour = cache.values().stream().collect(
            Collectors.groupingBy(
                  e -> {
                     Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+1"), Locale.ENGLISH);
                     c.setTime(e.departureTs);
                     return c.get(Calendar.HOUR_OF_DAY);
                  },
                  Collectors.counting()
            ));

      System.out.println(totalPerHour);

      Map<Integer, Long> delayedPerHour = cache.values().stream()
            .filter(e -> e.delayMin > 0)
            .collect(
                  Collectors.groupingBy(
                        e -> {
                           Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+1"), Locale.ENGLISH);
                           c.setTime(e.departureTs);
                           return c.get(Calendar.HOUR_OF_DAY);
                        },
                        Collectors.counting()
                  ));

      System.out.println(delayedPerHour);
   }

}