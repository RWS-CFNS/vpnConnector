import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

public class vpnConnector {
    private static String VPN_CMD;
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
    private static int REQUIRED_SPEED;
    private static String DATA_SENDER_JAR;
    private static int PROGRAM_TIMEOUT;

    public vpnConnector() {
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();

        try (FileInputStream fis = new FileInputStream("VPNconnector.properties")) {
            properties.load(fis);
            VPN_CMD = properties.getProperty("VPN_CMD");
            CONFIG_PATH = properties.getProperty("CONFIG_PATH");
            GPS_IP = properties.getProperty("GPS_IP");
            GPS_PORT = Integer.parseInt(properties.getProperty("GPS_PORT"));
            MIN_LAT = Double.parseDouble(properties.getProperty("MIN_LAT"));
            MAX_LAT = Double.parseDouble(properties.getProperty("MAX_LAT"));
            MIN_LON = Double.parseDouble(properties.getProperty("MIN_LON"));
            MAX_LON = Double.parseDouble(properties.getProperty("MAX_LON"));
            GPS_TIMEOUT = Integer.parseInt(properties.getProperty("GPS_TIMEOUT", "10"));
            LOG_DIRECTORY = properties.getProperty("LOG_DIRECTORY", "logs");
            SPEED_TEST_SERVER = properties.getProperty("SPEED_TEST_SERVER", "192.168.20.63");
            NUM_SPEED_TESTS = Integer.parseInt(properties.getProperty("NUM_SPEED_TESTS", "5"));
            SPEED_TEST_INTERVAL = Integer.parseInt(properties.getProperty("SPEED_TEST_INTERVAL", "10000"));
            SPEED_TEST_TIMEOUT = properties.getProperty("SPEED_TEST_TIMEOUT", "5");
            REQUIRED_SPEED = Integer.parseInt(properties.getProperty("REQUIRED_SPEED", "20"));
            DATA_SENDER_JAR = properties.getProperty("DATA_SENDER_JAR");
            PROGRAM_TIMEOUT = Integer.parseInt(properties.getProperty("PROGRAM_TIMEOUT", "20"));
        } catch (IOException e) {
            log("Fout bij het laden van de configuratie: " + e.getMessage());
            System.exit(1);
        }

    }

    private static void printConfigurationValues() {
        System.out.println("Configuration Values Loaded:");
        System.out.println("VPN_CMD = " + VPN_CMD);
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
        System.out.println("REQUIRED_SPEED = " + REQUIRED_SPEED);
        System.out.println("DATA_SENDER_JAR = " + DATA_SENDER_JAR);
        System.out.println("PROGRAM_TIMEOUT = " + PROGRAM_TIMEOUT);
    }

    private static void log(String message) {
        System.out.println(message);
        LocalDateTime now = LocalDateTime.now();
        File directory = new File(LOG_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        DateTimeFormatter entryFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            String formattedDate = now.format(fileFormatter);

            try (
                    FileWriter fw = new FileWriter(new File(directory, "log_" + formattedDate + ".txt"), true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw);
            ) {
                String formattedEntry = now.format(entryFormatter);
                out.println(formattedEntry + " - " + message);
            }
        } catch (IOException e) {
            System.err.println("Fout bij het loggen: " + e.getMessage());
            log("Fout bij het loggen: " + e.getMessage());
        }

    }

    public static boolean startVPN() {
        log("VPN start...");

        try {
            ProcessBuilder builder = new ProcessBuilder(new String[]{VPN_CMD, "up", CONFIG_PATH});
            Process process = builder.start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();

            String errorLine;
            while((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append("\n");
            }

            process.waitFor();
            if (process.exitValue() == 0) {
                log("VPN succesvol gestart.");
                long pingTime = pingHost(SPEED_TEST_SERVER);
                if (pingTime >= 0L) {
                    log("Ping tijd: " + pingTime + " ms.");
                    return true;
                } else {
                    log("geen succesvolle ping ontvangen.");
                    return false;
                }
            } else {
                log("Fout bij het verbinden van VPN\n" + errorOutput.toString());
                return false;
            }
        } catch (InterruptedException | IOException e) {
            log("Fout bij het verbinden met de VPN: " + ((Exception)e).getMessage());
            return false;
        }
    }

    public static boolean stopVPN() {
        log("VPN stop...");

        try {
            ProcessBuilder builder = new ProcessBuilder(new String[]{VPN_CMD, "down"});
            Process process = builder.start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();

            String errorLine;
            while((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append("\n");
            }

            process.waitFor();
            if (process.exitValue() == 0) {
                log("VPN is succesvol gestopt.");
                return true;
            } else {
                log("Er is een fout opgetreden bij het stoppen van VPN.\n" + errorOutput.toString());
                return false;
            }
        } catch (InterruptedException | IOException e) {
            log("Fout bij het verbreken van de VPN: " + ((Exception)e).getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        boolean vpnStatus = false;
        boolean dataVerstuurd = false;
        log("vpnConnector gestart.");
        while(true) {
            if (isOpLocatie()) {
                if (!vpnStatus) {
                    startVPN();
                    vpnStatus = true;
                }

                if (!dataVerstuurd) {
                    if (testInternetSpeed()) {
                        stuurData();
                        dataVerstuurd = true;
                    }
                } else {
                    log("Data is al verstuurd");
                }
            } else if (vpnStatus) {
                stopVPN();
                vpnStatus = false;
                dataVerstuurd = false;
            }

            try {
                Thread.sleep((long)PROGRAM_TIMEOUT);
            } catch (InterruptedException e) {
                log("Thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean isOpLocatie() {
        log("\nLocatie opvragen...");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(new Callable<Boolean>() {
            public Boolean call() {
                try {
                    try (Socket socket = new Socket(vpnConnector.GPS_IP, vpnConnector.GPS_PORT)) {
                        String line;
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                            while((line = reader.readLine()) != null) {
                                if (line.startsWith("$GPGGA") || line.startsWith("$GPRMC")) {
                                    String[] parts = line.split(",");
                                    int latIndex = line.startsWith("$GPGGA") ? 2 : 3;
                                    int lonIndex = line.startsWith("$GPGGA") ? 4 : 5;
                                    String latitude = vpnConnector.conversieDecimaalGetal(parts[latIndex], parts[latIndex + 1]);
                                    String longitude = vpnConnector.conversieDecimaalGetal(parts[lonIndex], parts[lonIndex + 1]);
                                    if (latitude != null && longitude != null) {
                                        vpnConnector.log("GPS Data ontvangen.");
                                        boolean locationStatus = vpnConnector.printCoordinaten(latitude, longitude);
                                        return locationStatus;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    vpnConnector.log("Fout bij het ophalen van GPS-gegevens: " + e.getMessage());
                }

                return false;
            }
        });

        boolean locationStatus;
        try {
            locationStatus = (Boolean)future.get((long)GPS_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception dataSent) {
            log("Geen GPS data ontvangen");
            locationStatus = false;
        } finally {
            executor.shutdownNow();
        }

        return locationStatus;
    }

    private static boolean printCoordinaten(String latitude, String longitude) {
        double lat = Double.parseDouble(latitude.replace(',', '.'));
        double lon = Double.parseDouble(longitude.replace(',', '.'));
        boolean isOpLocatie = lat >= MIN_LAT && lat <= MAX_LAT && lon >= MIN_LON && lon <= MAX_LON;
        if (isOpLocatie) {
            log("Coördinaten: " + latitude + ", " + longitude + " Het schip is in de haven van Scheveningen.");
        } else {
            log("Coördinaten: " + latitude + ", " + longitude + " Het schip is buiten de haven van Scheveningen.");
        }

        return isOpLocatie;
    }

    private static boolean stuurData() {
        Process process = null;

        boolean dataSent;
        boolean validdata = false;
        try {
            ProcessBuilder builder = new ProcessBuilder(new String[]{"java", "-jar", DATA_SENDER_JAR});
            process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            log("producerRMQ gestart...");

            String line;
            while((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains("sent")) {
                    validdata = true; // Zet dataSent op true als "sent" wordt gevonden in de uitvoer.
                }
            }

            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();

            while((line = errorReader.readLine()) != null) {
                System.err.println(line);
                log(line);
                errorOutput.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log("Er is een fout opgetreden bij het verzenden van de data.\n" + errorOutput.toString());
                dataSent = false;
            } else if(validdata) {
                log("Data succesvol verzonden.");
                dataSent = true;
            }
                else{
                log("Er is een fout opgetreden bij het verzenden van de data.\n" + errorOutput.toString());
                dataSent = false;
            }
        } catch (InterruptedException | IOException e) {
            log("Fout bij het uitvoeren van ProducerRBMQ: " + ((Exception)e).getMessage());
            dataSent = false;
        } finally {
            if (process != null) {
                process.destroy();
            }

        }

        return dataSent;
    }

    private static String conversieDecimaalGetal(String value, String direction) {
        if (value != null && !value.isEmpty() && direction != null) {
            boolean isLatitude = direction.equals("N") || direction.equals("S");
            int degreeLength = isLatitude ? 2 : 3;
            double degrees = Double.parseDouble(value.substring(0, degreeLength));
            double minutes = Double.parseDouble(value.substring(degreeLength));
            double decimalDegrees = degrees + minutes / (double)60.0F;
            if (direction.equals("S") || direction.equals("W")) {
                decimalDegrees = -decimalDegrees;
            }

            return String.format("%.6f", decimalDegrees);
        } else {
            return null;
        }
    }

    private static long pingHost(String host) {
        try {
            ProcessBuilder builder = new ProcessBuilder(new String[]{"ping", "-c", "1", host});
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while((line = reader.readLine()) != null) {
                if (line.contains("time=")) {
                    String[] parts = line.split("time=");
                    String pingTime = parts[1].split(" ")[0];
                    return (long)Double.parseDouble(pingTime);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            log("Fout bij het pingen van de host: " + e.getMessage());
        }

        return -1L;
    }

    private static boolean testInternetSpeed() {
        int numTests = NUM_SPEED_TESTS;
        int interval = SPEED_TEST_INTERVAL;
        double[] transferSpeeds = new double[numTests];

        try {
            for(int i = 0; i < numTests; ++i) {
                ProcessBuilder processBuilder = new ProcessBuilder(new String[]{"timeout", SPEED_TEST_TIMEOUT, "iperf3", "-c", SPEED_TEST_SERVER, "-J", "-t", "5", "-O", "4"});
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();

                String line;
                while((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                process.waitFor();
                if (output.length() == 0) {
                    System.err.println("Geen uitvoer van iperf3. Controleer de verbinding.");
                    log("Geen uitvoer van iperf3. Controleer de verbinding.");
                    return false;
                }

                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(output.toString());
                } catch (JSONException e) {
                    System.err.println("Fout bij het parseren van JSON: " + e.getMessage());
                    log("Fout bij het parseren van JSON: " + e.getMessage());
                    return false;
                }

                if (!jsonObject.has("end") || !jsonObject.getJSONObject("end").has("streams")) {
                    System.err.println("Ongeldige JSON-structuur. Geen 'streams' gevonden.");
                    log("Ongeldige JSON-structuur. Geen 'streams' gevonden.");
                    return false;
                }

                try {
                    double transferSpeed = (double)Math.round(jsonObject.getJSONObject("end").getJSONArray("streams").getJSONObject(0).getJSONObject("sender").getDouble("bits_per_second") / (double)1000000.0F);
                    transferSpeeds[i] = transferSpeed;
                    log("Test " + (i + 1) + ": Transfer speed: " + transferSpeed + " Mb per second");
                } catch (JSONException e) {
                    System.err.println("Fout bij het extraheren van de transfer speed: " + e.getMessage());
                    log("Test " + (i + 1) + ": Transfer speed: 0 Mb per second");
                }

                Thread.sleep((long)interval);
            }

            double averageSpeed = calculateAverage(transferSpeeds);
            log(Arrays.toString(transferSpeeds));
            log("Gemiddelde snelheid gedurende " + numTests + " testen: " + averageSpeed + " Mb second");
            if (calculateStability(transferSpeeds, averageSpeed)) {
                log("Verbinding Stabiel.");
                return true;
            }
        } catch (InterruptedException | IOException e) {
            System.err.println("Error testing internet speed: " + ((Exception)e).getMessage());
            log("Error testing internet speed: " + ((Exception)e).getMessage());
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            log("Error parsing JSON: " + e.getMessage());
        }

        return false;
    }

    private static double calculateAverage(double[] speeds) {
        double sum = (double)0.0F;

        for(double speed : speeds) {
            sum += speed;
        }

        return sum / (double)speeds.length;
    }

    private static boolean calculateStability(double[] speeds, double averageSpeeds) {
        double averageLow = averageSpeeds * (double)0.5F;
        double averageHigh = averageSpeeds * (double)1.5F;

        for(double speed : speeds) {
            if (speed < averageLow || speed > averageHigh) {
                log("Verbinding niet stabiel: " + speed + " wijkt teveel af van " + averageSpeeds);
                return false;
            }

            if (averageSpeeds < (double)REQUIRED_SPEED) {
                log("Verbinding niet stabiel, verbindingssnelheid is te laag: " + averageSpeeds + "MB/s");
                return false;
            }
        }

        return true;
    }

    static {
        loadConfiguration();
        stopVPN();
    }
}
