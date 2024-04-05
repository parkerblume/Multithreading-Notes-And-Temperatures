/*
 * This program introduces a scale for faster results to the user, it's defined:
 * First hour becomes minute, minute becomes second, then it gets cut in half.
 * 1 hour report -> defaults to 30 second report
 * 1 minute sensor interval for scan -> defaults to half a second
 * 10 minute interval for temp difference -> defaults to 5 seconds
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

class TemperatureReading 
{
    private int temperature;
    private long timestamp;

    public TemperatureReading(int temperature, long timestamp) 
    {
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public int getTemperature() 
    {
        return temperature;
    }

    public long getTimestamp() 
    {
        return timestamp;
    }
}

class TemperatureSensor extends Thread 
{
    private double sensorInterval;
    private AtomicBoolean stopSensor;
    private List<TemperatureReading> temperatureReadings;

    public TemperatureSensor(AtomicBoolean stopSensor, List<TemperatureReading> temperatureReadings, double sensorInterval) 
    {
        this.stopSensor = stopSensor;
        this.temperatureReadings = temperatureReadings;
        this.sensorInterval = sensorInterval;
    }

    @Override
    public void run() 
    {
        Random random = new Random();
        while (!stopSensor.get()) {
            int temperature = random.nextInt(171) - 100; // Temp between (-100F, 70F);
            long timestamp = System.currentTimeMillis();
            // System.out.println(temperature);

            synchronized (temperatureReadings) 
            {
                temperatureReadings.add(new TemperatureReading(temperature, timestamp));
            }

            try {
                Thread.sleep((long) sensorInterval); // Possibly scaled sensor interval (default is every second) 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class TemperatureModule extends Thread 
{
    private double timeScale; // used for the 10-min interval reading
    private double reportInterval;
    private int numReports;
    private AtomicBoolean stopReport;
    private List<TemperatureReading> temperatureReadings;

    public TemperatureModule(AtomicBoolean stopReport, List<TemperatureReading> temperatureReadings, 
                             double reportInterval, int numReports, double timeScale) 
    {
        this.stopReport = stopReport;
        this.temperatureReadings = temperatureReadings;
        this.reportInterval = reportInterval;
        this.numReports = numReports;
        this.timeScale = timeScale;
    }

    @Override
    public void run() 
    {
        int hourCount = 1;
        while (hourCount <= numReports && !stopReport.get()) 
        {
            // Sleep until scanners are done with their temperature readings.
            try {
                Thread.sleep((long) reportInterval); // Scaled report interval (default is 30 seconds)
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            synchronized (temperatureReadings) 
            {
                if (!temperatureReadings.isEmpty()) 
                {
                    // get the most recent hourly readings, then clear the shared memory space ready for new data
                    List<TemperatureReading> hourlyReadings = new ArrayList<>(temperatureReadings);
                    temperatureReadings.clear();

                    // Sorts hourly readings -> lowest to highest temp for easier report generation
                    Collections.sort(hourlyReadings, (r1, r2) -> Integer.compare(r1.getTemperature(), r2.getTemperature()));

                    System.out.println("\tHourly Temperature Report - Hour " + hourCount);
                    System.out.println("==================================================");
                    generateReport(hourlyReadings);
                    System.out.println();

                    hourCount++;
                }
            }
        }
    }

    private void generateReport(List<TemperatureReading> hourlyReadings) 
    {
        int size = hourlyReadings.size();

        System.out.println("Top 5 Highest Temperatures:");
        for (int i = size - 1; i >= size - 5 && i >= 0; i--) 
        {
            TemperatureReading reading = hourlyReadings.get(i);
            System.out.println("Temperature: " + reading.getTemperature() + "F, Timestamp: " + reading.getTimestamp());
        }

        System.out.println("\nTop 5 Lowest Temperatures:");
        for (int i = 0; i < 5 && i < size; i++) 
        {
            TemperatureReading reading = hourlyReadings.get(i);
            System.out.println("Temperature: " + reading.getTemperature() + "F, Timestamp: " + reading.getTimestamp());
        }

        // 10-min (default 5 second) interval for temp difference
        double intervalDuration = 5000 * timeScale; // 10min -> 5 seconds, multiply by the given time scale to keep everything in line

        int largestDiff = Integer.MIN_VALUE;
        int largestDiffStart = 0;
        int largestDiffEnd = 0;
        for (int i = 0; i < size - 1; i++) 
        {
            for (int j = i + 1; j < size; j++) 
            {
                // first grab the difference, then compare (using absolutes)
                int diff = Math.abs(hourlyReadings.get(i).getTemperature() - hourlyReadings.get(j).getTemperature());

                if (diff > largestDiff && hourlyReadings.get(j).getTimestamp() - hourlyReadings.get(i).getTimestamp() <= intervalDuration) 
                {
                    largestDiff = diff;
                    largestDiffStart = i;
                    largestDiffEnd = j;
                }
            }
        }

        System.out.println("\n10-Minute Interval with Largest Temperature Difference:");
        System.out.println("Start: Temperature: " + hourlyReadings.get(largestDiffStart).getTemperature() +
                            "F, Timestamp: " + hourlyReadings.get(largestDiffStart).getTimestamp());
        System.out.println("End: Temperature: " + hourlyReadings.get(largestDiffEnd).getTemperature() +
                            "F, Timestamp: " + hourlyReadings.get(largestDiffEnd).getTimestamp());
        System.out.println("Temperature Difference: " + largestDiff + "F");
    }
}

public class AtmosphericTemperatureReport 
{
    private static final int NUM_SENSORS = 8; // Rover has 8 sensors (threads)

    public static void main(String[] args) {

        // default values if not provided in cmd line args
        double timeScale = 1;
        int numReports = 1;

        if (args.length > 0)
        {
            try {
                timeScale = Double.parseDouble(args[0]);
                if (timeScale <= 0)
                {
                    System.err.println("Can't scale less by 0 or a negative, defaulting to 1.");
                    timeScale = 1;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number for scale, defaulting to 1.");
            }
        }

        if (args.length > 1)
        {
            try {
                numReports = Integer.parseInt(args[1]);
                if (numReports <= 0)
                {
                    System.err.println("Can't have 0 or negative reports silly, defaulting to 1.");
                    numReports = 1;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number for reports, defaulting to 1.");
            }
        }

        double sensorInterval = 500 * timeScale; // Sensors scan every 1 minute (half a second), we scale from 0.5 seconds (500ms).
        double reportInterval = 30000 * timeScale; // Reports are generated every hour (30 seconds), we scale from 30 seconds (30000ms).
        int monitoringInterval = (int) reportInterval * numReports; // We monitor for however long we need to be able to make each report.

        System.out.println("You are wanting [" + numReports + "] report(s), so we will be monitoring for about " + 
                            "[" + monitoringInterval + " ms] (" + (monitoringInterval / 1000 ) + " s).");
        System.out.println("Each report will take about [" + reportInterval + " ms] (" + (reportInterval / 1000) + " s).");
        System.out.println("Starting our cute rover, reports will be generated soon...");

        long startTime = System.currentTimeMillis();
        // Define list as our shared memory space for the temp readings
        List<TemperatureReading> temperatureReadings = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean stopSensors = new AtomicBoolean(false);
        AtomicBoolean stopReport = new AtomicBoolean(false);

        // Turn our sensors into threads and start them
        TemperatureSensor[] sensors = new TemperatureSensor[NUM_SENSORS];
        for (int i = 0; i < NUM_SENSORS; i++) 
        {
            sensors[i] = new TemperatureSensor(stopSensors, temperatureReadings, sensorInterval);
            sensors[i].start();
        }

        // We have our report running on a seperate module (thread) for easier report generation
        // It has access to the shared memory space
        TemperatureModule report = new TemperatureModule(stopReport, temperatureReadings, reportInterval, numReports, timeScale);
        report.start();

        // Simulates our monitoring for the gathering the number of reports, etc.
        try {
            Thread.sleep(monitoringInterval);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Signal the threads to stop
        stopSensors.set(true);
        stopReport.set(true);

        // Wait for all threads to finish
        try {
            for (int i = 0; i < NUM_SENSORS; i++) 
            {
                sensors[i].join();
            }

            report.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Our Rover is now taking a monitoring break because he can.");
        System.out.println("This took " + (endTime - startTime) + " ms.");
    }
}