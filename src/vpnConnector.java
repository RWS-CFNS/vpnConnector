import org.json.JSONException;
import org.json.JSONObject;
import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Properties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class vpnConnector {

    private static String WIREGUARD_CMD;
    private static String CONFIG_PATH;
    private static String GPS_IP;
    private static int GPS_PORT;

    private static double MIN_LAT;
    private static double MAX_LAT;
    private static double MIN_LON;
    private static double MAX_LON;

    private static int GPS_TIMEOUT;
    private static String LOG_DIRECTORY;
    private static String SPEED_TEST_SERVER;
    private static int NUM_SPEED_TESTS;
    private static int SPEED_TEST_INTERVAL;
    private static String SPEED_TEST_TIMEOUT;
    private static String DATA_SENDER_JAR;

    static {
        loadConfiguration();
//        printConfigurationValues();
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("VPNmanager.properties")) {
            properties.load(fis);
            WIREGUARD_CMD = properties.getProperty("WIREGUARD_CMD");
            CONFIG_PATH = properties.getProperty("CONFIG_PATH");
            GPS_IP = properties.getProperty("GPS_IP");
            GPS_PORT = Integer.parseInt(properties.getProperty("GPS_PORT"));
            MIN_LAT = Double.parseDouble(properties.getProperty("MIN_LAT"));
            MAX_LAT = Double.parseDouble(properties.getProperty("MAX_LAT"));
            MIN_LON = Double.parseDouble(properties.getProperty("MIN_LON"));
            MAX_LON = Double.parseDouble(properties.getProperty("MAX_LON"));
            GPS_TIMEOUT = Integer.parseInt(properties.getProperty("GPS_TIMEOUT", "10")); // Default to 10 seconds
            LOG_DIRECTORY = properties.getProperty("LOG_DIRECTORY", "logs"); // Default directory
            SPEED_TEST_SERVER = properties.getProperty("SPEED_TEST_SERVER","192.168.20.63");
            NUM_SPEED_TESTS = Integer.parseInt(properties.getProperty("NUM_SPEED_TESTS", "5")); // Default to 5
            SPEED_TEST_INTERVAL = Integer.parseInt(properties.getProperty("SPEED_TEST_INTERVAL", "10000")); // Default to 10 seconds
            SPEED_TEST_TIMEOUT = properties.getProperty("SPEED_TEST_TIMEOUT", "5"); // Default to 5 seconds
            DATA_SENDER_JAR = properties.getProperty("DATA_SENDER_JAR");
        } catch (IOException e) {
            log("Fout bij het laden van de configuratie: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printConfigurationValues() {
        System.out.println("Configuration Values Loaded:");
        System.out.println("WIREGUARD_CMD = " + WIREGUARD_CMD);
        System.out.println("CONFIG_PATH = " + CONFIG_PATH);
        System.out.println("GPS_IP = " + GPS_IP);
        System.out.println("GPS_PORT = " + GPS_PORT);
        System.out.println("MIN_LAT = " + MIN_LAT);
        System.out.println("MAX_LAT = " + MAX_LAT);
        System.out.println("MIN_LON = " + MIN_LON);
        System.out.println("MAX_LON = " + MAX_LON);
        System.out.println("GPS_TIMEOUT = " + GPS_TIMEOUT);
        System.out.println("LOG_DIRECTORY = " + LOG_DIRECTORY);
        System.out.println("SPEED_TEST_SERVER = " + SPEED_TEST_SERVER);
        System.out.println("NUM_SPEED_TESTS = " + NUM_SPEED_TESTS);
        System.out.println("SPEED_TEST_INTERVAL = " + SPEED_TEST_INTERVAL);
        System.out.println("SPEED_TEST_TIMEOUT = " + SPEED_TEST_TIMEOUT);
        System.out.println("DATA_SENDER_JAR = " + DATA_SENDER_JAR);
    }

    private static void log(String message) {
        System.out.println(message); // Print naar console
        LocalDateTime now = LocalDateTime.now();
        File directory = new File(LOG_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory if it does not exist
        }
        DateTimeFormatter entryformatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter fileformatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        // Append the log message to log.txt
        try (FileWriter fw = new FileWriter(new File(directory, "log_" + now.format(fileformatter) + ".txt"), true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(now.format(entryformatter) + " - " + message);
        } catch (IOException e) {
            System.err.println("Fout bij het loggen: " + e.getMessage());
            log("Fout bij het loggen: " + e.getMessage());
        }
    }

    public static boolean startWireGuardVPN() {
        log("WireGuard VPN start...");
        try {
            File configFile = new File(CONFIG_PATH);
            if (!configFile.exists()) {
                log("Configuratiebestand niet gevonden: " + CONFIG_PATH);
                return false;
            }

            ProcessBuilder builder = new ProcessBuilder(WIREGUARD_CMD, "up", CONFIG_PATH);
            Process process = builder.start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append("\n");
            }
            process.waitFor();

            if (process.exitValue() == 0) {
                log("VPN succesvol gestart.");
                long pingTime = pingHost("8.8.8.8");

                if (pingTime >= 0) {
                    log("Ping tijd: " + pingTime + " ms.");
                    return true;
                } else {
                    log("geen succesvolle ping ontvangen.");
                    return false;
                }
            } else {
                log("Fout bij het verbinden van WireGuard VPN\n" + errorOutput.toString());
                return false;
            }

        } catch (IOException | InterruptedException e) {
            log("Fout bij het verbinden met de VPN: " + e.getMessage());
            return false;
        }
    }

    public static boolean stopWireGuardVPN() {
        log("WireGuard VPN stop...");
        try {
            ProcessBuilder builder = new ProcessBuilder(WIREGUARD_CMD, "down", CONFIG_PATH);
            Process process = builder.start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append("\n");
            }
            process.waitFor();

            if (process.exitValue() == 0) {
                log("WireGuard VPN is succesvol gestopt.");
                return true;
            } else {
                log("Er is een fout opgetreden bij het stoppen van WireGuard VPN.\n" + errorOutput.toString());
                return false;
            }

        } catch (IOException | InterruptedException e) {
            log("Fout bij het verbreken van de VPN: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        boolean vpnStatus = false;
        boolean dataVerstuurd = false;
        log("vpnConnector gestart.");
        while (true) {
            // Check gps gegevens
                if (isOpLocatie()) {
                    //Start vpn als deze nog niet aan staat
                    if (!vpnStatus){
                        startWireGuardVPN();
                        vpnStatus = true;
                    }
                    if(!dataVerstuurd) {
                        if(testInternetSpeed()) {
                            stuurData();
                            dataVerstuurd = true;
                        }
                    }else{
                        log("Data is al verstuurd");
                    }
                } else {
                    if (vpnStatus) {
                        stopWireGuardVPN();
                        vpnStatus = false;
                        dataVerstuurd = false;
                    }
                }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log("Thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    // Controleer of de huidige GPS-locatie binnen het bereik ligt

    private static boolean isOpLocatie() {
        log("\nLocatie opvragen...");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try (Socket socket = new Socket(GPS_IP, GPS_PORT);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        //log("GPS data: " + line);
                        if (line.startsWith("$GPGGA") || line.startsWith("$GPRMC")) {
                            String[] parts = line.split(",");
                            int latIndex = line.startsWith("$GPGGA") ? 2 : 3;
                            int lonIndex = line.startsWith("$GPGGA") ? 4 : 5;

                            String latitude = conversieDecimaalGetal(parts[latIndex], parts[latIndex + 1]);
                            String longitude = conversieDecimaalGetal(parts[lonIndex], parts[lonIndex + 1]);

                            if (latitude != null && longitude != null) {
                                log("GPS Data ontvangen.");
                                return printCoordinaten(latitude, longitude);
                            }
                        }
                    }
                } catch (Exception e) {
                    log("Fout bij het ophalen van GPS-gegevens: " + e.getMessage());
                }
                return false;
            }
        });

        try {
            // Wacht maximaal 10 seconden op het resultaat
            return future.get(GPS_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) {
            log("Geen GPS data ontvangen");
            return false; // Retourneer false als er geen data is ontvangen
        } finally {
            executor.shutdownNow(); // Stop de thread
        }
    }



    private static boolean printCoordinaten(String latitude, String longitude) {
        double lat = Double.parseDouble(latitude.replace(',', '.'));
        double lon = Double.parseDouble(longitude.replace(',', '.'));

        boolean isOpLocatie = (lat >= MIN_LAT) && (lat <= MAX_LAT) && (lon >= MIN_LON) && (lon <= MAX_LON);

        if (isOpLocatie) {
            log("Coördinaten: " + latitude + ", " + longitude + " Het schip is in de haven van Scheveningen.");
        } else {
            log("Coördinaten: " + latitude + ", " + longitude + " Het schip is buiten de haven van Scheveningen.");
        }

        return isOpLocatie;
    }



    private static boolean stuurData() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", DATA_SENDER_JAR);
            process = builder.start();

            // Read the output from the process
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            log("producerRMQ gestart...");
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Print to terminal
            }

            // Optionally, read the error stream
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line); // Print error to terminal
                log(line);
                errorOutput.append(line).append("\n");
            }

            // Wait for the process to complete
            int exitCode = process.waitFor(); // This will block until the process is finished

            // Check exit code after the process has finished
            if (exitCode == 0) {
                log("Data succesvol verzonden.");
                return true;
            } else {
                log("Er is een fout opgetreden bij het verzenden van de data.\n" + errorOutput.toString());
            }
        } catch (IOException | InterruptedException e) {
            log("Fout bij het uitvoeren van ProducerRBMQ: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy(); // Clean up the process if it was started
            }
        }
        return false;
    }



    private static String conversieDecimaalGetal(String value, String direction) {
        if (value == null || value.isEmpty() || direction == null) return null;

        boolean isLatitude = direction.equals("N") || direction.equals("S");
        int degreeLength = isLatitude ? 2 : 3;

        double degrees = Double.parseDouble(value.substring(0, degreeLength));
        double minutes = Double.parseDouble(value.substring(degreeLength));
        double decimalDegrees = degrees + (minutes / 60);

        if (direction.equals("S") || direction.equals("W")) {
            decimalDegrees = -decimalDegrees;
        }

        return String.format("%.6f", decimalDegrees);
    }

    // Methode om een host te pingen
    private static long pingHost(String host) {
        try {
            ProcessBuilder builder = new ProcessBuilder("ping", "-c", "1", host);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("time=")) {
                    // Haal de pingtijd op uit de regel met "time="
                    String[] parts = line.split("time=");
                    String pingTime = parts[1].split(" ")[0];
                    return (long) Double.parseDouble(pingTime); // Retourneer de pingtijd in milliseconden
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log("Fout bij het pingen van de host: " + e.getMessage());
        }
        return -1; // Retourneer -1 als de ping niet succesvol is
    }

    private static boolean testInternetSpeed() {
        int numTests = NUM_SPEED_TESTS; // Number of tests
        int interval = SPEED_TEST_INTERVAL; // Interval in milliseconds
        double[] transferSpeeds = new double[numTests]; // Array to store transfer speeds


        try {
            for (int i = 0; i < numTests; i++) {
                ProcessBuilder processBuilder = new ProcessBuilder("timeout", SPEED_TEST_TIMEOUT, "iperf3", "-c", SPEED_TEST_SERVER, "-J", "-t", "1");
                Process process = processBuilder.start();

                // Read the output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                process.waitFor();

                // Controleer de output
                if (output.length() == 0) {
                    System.err.println("Geen uitvoer van iperf3. Controleer de verbinding.");
                    log("Geen uitvoer van iperf3. Controleer de verbinding.");
                    return false; // Of geef een foutmelding en ga verder
                }

                // Print de output om te inspecteren
                //log("Output van iperf3:\n" + output.toString());

                // Parse the JSON output
                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(output.toString());
                } catch (JSONException e) {
                    System.err.println("Fout bij het parseren van JSON: " + e.getMessage());
                    log("Fout bij het parseren van JSON: " + e.getMessage());
                    return false;
                }

                // Controleer op de aanwezigheid van de vereiste velden
                if (!jsonObject.has("end") || !jsonObject.getJSONObject("end").has("streams")) {
                    System.err.println("Ongeldige JSON-structuur. Geen 'streams' gevonden.");
                    log("Ongeldige JSON-structuur. Geen 'streams' gevonden.");
                    return false; // Of geef een foutmelding en ga verder
                }

                // Extract transfer speed from "end" -> "streams" -> "sender"
                try {
                    double transferSpeed = Math.round(jsonObject.getJSONObject("end")
                            .getJSONArray("streams")
                            .getJSONObject(0)
                            .getJSONObject("sender")
                            .getDouble("bits_per_second") / 8000000);

                    transferSpeeds[i] = transferSpeed; // Store the speed in the array

                    // Print the speed for this test to the terminal
                    log("Test " + (i + 1) + ": Transfer speed: " + transferSpeed + " MB per second");
                } catch (JSONException e) {
                    System.err.println("Fout bij het extraheren van de transfer speed: " + e.getMessage());
                    log("Test " + (i + 1) + ": Transfer speed: " + 0 + " MB per second");
                    //log("Bekijk de output voor details:\n" + output.toString());
                }

                // Wait for the specified interval before the next test
                Thread.sleep(interval);
            }

            // Calculate the average speed
            double averageSpeed = calculateAverage(transferSpeeds);
            log(Arrays.toString(transferSpeeds));
            log("Gemiddelde snelheid gedurende " + numTests + " testen: " + averageSpeed + " MB second");

            if (calculateStability(transferSpeeds, averageSpeed)) {
                log("Verbinding Stabiel.");
                return true;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error testing internet speed: " + e.getMessage());
            log("Error testing internet speed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            log("Error parsing JSON: " + e.getMessage());
            }
        return false;
    }




    private static double calculateAverage(double[] speeds) {
        double sum = 0;
        for (double speed : speeds) {
            sum += speed;
        }
        return sum / speeds.length; // Return the average
    }

    private static boolean calculateStability(double[] speeds, double averagespeeds) {
        double averagelow = averagespeeds * 0.8;
        double averagehigh = averagespeeds * 1.2;
        for (double speed : speeds) {
            if (speed < averagelow || speed > averagehigh){
                log("Verbinding niet stabiel: " + speed + " wijkt teveel af van " + averagespeeds);
                return false;
            }
            else if (averagespeeds < 20){
                log("Verbinding niet stabiel, verbindingssnelheid is te laag: " + averagespeeds + "MB/s");
                return false;
            }
        }
        return true;
    }
}
