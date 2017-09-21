package ta4jexamples.loaders;

import au.com.bytecode.opencsv.CSVReader;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuandlTicksFileLoader {

  public static class QuandlDataFeed {
    private final String quandlCode;
    private final DateTime startDate;
    private final DateTime endDate;

    private static final String ISO_8601_DATE_FMT = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern(ISO_8601_DATE_FMT);

    // changes these to a Builder if more params
    public QuandlDataFeed(String quandlCode) {
      this(quandlCode, DateTime.now().minusYears(1), DateTime.now());
    }

    public QuandlDataFeed(String quandlCode, DateTime startdate) {
      this(quandlCode, startdate, DateTime.now());
    }

    public QuandlDataFeed(String quandlCode, DateTime startDate, DateTime endDate) {
      this.quandlCode = quandlCode;
      this.startDate = startDate;
      this.endDate = endDate;
    }

    public TimeSeries load() {
      return get(buildURL());
    }

    private URL buildURL() {
      try {
         File file = new File("/home/tonyj/Data/"+quandlCode+".csv");
         return file.toURI().toURL();
      } catch (MalformedURLException e) {
        throw new RuntimeException("Invalid URL");
      }
    }

    private TimeSeries toTimeSeries(InputStream is) {
      List<Tick> ticks = new ArrayList<>();

      CSVReader csvReader = new CSVReader(new InputStreamReader(is, Charset.forName("UTF-8")), ',', '"', 1);
      try {
        String[] line;
        while ((line = csvReader.readNext()) != null) {
          DateTime date = new DateTime(DATE_FORMATTER.parseDateTime(line[1]));
          if (date.isBefore(startDate) || date.isAfter(endDate)) continue;
          double open = Double.parseDouble(line[2]);
          double high = Double.parseDouble(line[3]);
          double low = Double.parseDouble(line[4]);
          double close = Double.parseDouble(line[5]);
          double volume = Double.parseDouble(line[6]);

          ticks.add(new Tick(date, open, high, low, close, volume));
        }
      } catch (IOException ioe) {
        Logger.getLogger(QuandlTicksFileLoader.class.getName()).log(Level.SEVERE, "Unable to load ticks from CSV", ioe);
      } catch (NumberFormatException nfe) {
        Logger.getLogger(QuandlTicksFileLoader.class.getName()).log(Level.SEVERE, "Error while parsing value", nfe);
      }

      return new TimeSeries(quandlCode + "_ticks", ticks);
    }

    private TimeSeries get(URL url) {
      URLConnection connection = null;
      for (int t=0; t<10; t++) {
        try {
          connection = url.openConnection();
          return toTimeSeries(new BufferedInputStream(connection.getInputStream()));
        } catch (FileNotFoundException fnfe) {
          throw new RuntimeException(String.format("%s is not a valid Quandl URL", url));
        } catch (IOException e) {
        } 
      }
      throw new RuntimeException("failed to read "+url+" after 10 retries");
    }
  }

  public static void main(String args[]) {
    QuandlDataFeed quandl = new QuandlDataFeed("WIKI-IRBT");
    TimeSeries series = quandl.load();

    System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
    System.out.println("Number of ticks: " + series.getTickCount());
    System.out.println("Last tick: \n"
            + "\tVolume: " + series.getLastTick().getVolume() + "\n"
            + "\tOpen price: " + series.getLastTick().getOpenPrice()+ "\n"
            + "\tClose price: " + series.getLastTick().getClosePrice());
  }
}