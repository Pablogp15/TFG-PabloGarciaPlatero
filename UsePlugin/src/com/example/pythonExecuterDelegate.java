package com.example;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.sys.MObjectState;
import utils.DTLogger;
import utils.DTUseFacade;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plugin que ejecuta algoritmos en Python y guarda los resultados en el modelo USE.
 */
public class pythonExecuterDelegate implements IPluginActionDelegate {

    private ExecutorService executor;
    private boolean isRunning;
    private Thread algorithmRunnerThread;
    private DTUseFacade useApi;
    private PythonAlgorithmRunner algorithmRunner;

    public pythonExecuterDelegate() {
        ensureThreadPool();
        isRunning = false;
    }

    public void performAction(IPluginAction pluginAction) {
        if (!isRunning) {
            start(pluginAction);
        } else {
            stop();
        }
    }

    private void start(IPluginAction pluginAction) {
        setApi(pluginAction);
        initializeModel();
        algorithmRunner = new PythonAlgorithmRunner(useApi);
        ensureThreadPool();
        algorithmRunnerThread = new Thread(algorithmRunner, "Python Algorithm Runner Thread");
        algorithmRunnerThread.start();
        isRunning = true;
        DTLogger.info("Python algorithm runner started.");
    }

    private void stop() {
        try {
            DTLogger.info("Stopping...");
            algorithmRunner.stop();
            algorithmRunnerThread.join();
            executor.shutdown();
            isRunning = false;
            DTLogger.info("Execution stopped successfully");
        } catch (InterruptedException ex) {
            DTLogger.error("Could not stop execution successfully", ex);
        }
    }

    private void setApi(IPluginAction pluginAction) {
        UseSystemApi api = UseSystemApi.create(pluginAction.getSession());
        useApi = new DTUseFacade(api);
    }

    private void ensureThreadPool() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(1);
        }
    }

    private void initializeModel() {
        try {
            String executionId = System.currentTimeMillis() + "";
            for (MObjectState obj : useApi.getObjectsOfClass("AlgorithmResult")) {
                useApi.setAttribute(obj, "executionId", executionId);
                useApi.setAttribute(obj, "temperatura", "0");
                useApi.setAttribute(obj, "luminosidad", "0");
            }
        } catch (Exception ex) {
            DTLogger.error("Error initializing USE model:", ex);
        }
    }

    private class PythonAlgorithmRunner implements Runnable {
        private final DTUseFacade useApi;
        private volatile boolean running;

        public PythonAlgorithmRunner(DTUseFacade useApi) {
            this.useApi = useApi;
            this.running = true;
        }

        @Override
        public void run() {
            try {
                String result = executePythonScript();
                if (result != null) {
                    saveResultToModel(result);
                }
                DTLogger.info("Python algorithm execution completed.");
            } catch (Exception ex) {
                DTLogger.error("Error running Python algorithm:", ex);
            } finally {
                // Asegurarse de que el plugin termine
                stop();
            }
        }

        private String executePythonScript() {
            try {
                // Obtener los valores desde el modelo USE
                Integer mesPrediccion = null;
                Double temperaturaDeseada = null;
                Double luminosidadDeseada = null;
                Double humedadActual = null;
                Double CO2Actual = null;

                useApi.updateDerivedValues();

                MObjectState configObj = useApi.getAnyObjectOfClass("Habitacion");
                if (configObj != null) {
                    temperaturaDeseada = useApi.getRealAttribute(configObj, "temperaturaIdealPromedio");
                    if (temperaturaDeseada == null) {
                        DTLogger.error("No se pudo leer 'temperaturaIdealPromedio'.");
                        return null;
                    }

                    luminosidadDeseada = useApi.getRealAttribute(configObj, "luzIdealPromedio");
                    if (luminosidadDeseada == null) {
                        DTLogger.error("No se pudo leer 'luzIdealPromedio'.");
                        return null;
                    }

                    MObjectState tiempo = useApi.getAnyObjectOfClass("Tiempo");
                    if (tiempo != null) {
                        mesPrediccion = useApi.getIntegerAttribute(tiempo, "mes");
                        if (mesPrediccion == null) {
                            DTLogger.error("No se pudo leer 'mes'.");
                            return null;
                        }
                    } else {
                        DTLogger.error("No se encontró 'Tiempo'.");
                        return null;
                    }

                    MObjectState humedad = useApi.getAnyObjectOfClass("SensorHumedad");
                    if (humedad != null) {
                        humedadActual = useApi.getRealAttribute(humedad, "valor");
                        if (humedadActual == null) {
                            DTLogger.error("No se pudo leer 'valor' de SensorHumedad.");
                            return null;
                        }
                    } else {
                        DTLogger.error("No se encontró 'SensorHumedad'.");
                        return null;
                    }

                    MObjectState co2 = useApi.getAnyObjectOfClass("SensorCO2");
                    if (co2 != null) {
                        CO2Actual = useApi.getRealAttribute(co2, "valor");
                        if (CO2Actual == null) {
                            DTLogger.error("No se pudo leer 'valor' de SensorCO2.");
                            return null;
                        }
                    } else {
                        DTLogger.error("No se encontró 'SensorCO2'.");
                        return null;
                    }
                } else {
                    DTLogger.error("No se encontró 'Habitacion'.");
                    return null;
                }

                DTLogger.info("Valores obtenidos: mesPrediccion=" + mesPrediccion + ", temperaturaDeseada=" + temperaturaDeseada +
                        ", luminosidadDeseada=" + luminosidadDeseada + ", humedadActual=" + humedadActual + ", CO2Actual=" + CO2Actual);

                String mesPrediccionStr = String.valueOf(mesPrediccion);
                String temperaturaDeseadaStr = String.format(Locale.US, "%.1f", temperaturaDeseada);
                String luminosidadDeseadaStr = String.format(Locale.US, "%.1f", luminosidadDeseada);
                String humedadActualStr = String.format(Locale.US, "%.1f", humedadActual);
                String CO2ActualStr = String.format(Locale.US, "%.1f", CO2Actual);

                DTLogger.info("Valores como String: mesPrediccionStr=" + mesPrediccionStr + ", temperaturaDeseadaStr=" + temperaturaDeseadaStr +
                        ", luminosidadDeseadaStr=" + luminosidadDeseadaStr + ", humedadActualStr=" + humedadActualStr + ", CO2ActualStr=" + CO2ActualStr);

                ProcessBuilder pb = new ProcessBuilder(
                        "python",
                        "C:/UMA/4/TFG/linearRegressionTempLuz.py",
                        mesPrediccionStr,
                        temperaturaDeseadaStr,
                        luminosidadDeseadaStr
                );
                pb.directory(new File("C:/UMA/4/TFG"));
                pb.redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                ProcessBuilder pb2 = new ProcessBuilder(
                        "python",
                        "C:/UMA/4/TFG/KNNhumedadPureza.py",
                        humedadActualStr,
                        CO2ActualStr
                );
                pb2.directory(new File("C:/UMA/4/TFG"));
                pb2.redirectErrorStream(true);
                Process process2 = pb2.start();

                StringBuilder output2 = new StringBuilder();
                BufferedReader reader2 = new BufferedReader(new InputStreamReader(process2.getInputStream(), StandardCharsets.UTF_8));
                String line2;
                while ((line2 = reader2.readLine()) != null) {
                    output2.append(line2).append("\n");
                }

                int exitCode = process.waitFor();
                int exitCode2 = process2.waitFor();

                if (exitCode == 0 && exitCode2 == 0) {
                    String result1 = output.toString().trim();
                    String result2 = output2.toString().trim();
                    String combinedResult = result1 + "," + result2; // Combinar en un solo string con cuatro valores
                    DTLogger.info("Combined result: " + combinedResult); // Log para depuración
                    return combinedResult;
                } else {
                    DTLogger.error("Python script failed with exit codes: " + exitCode + ", " + exitCode2 + ", outputs: " + output.toString() + ", " + output2.toString());
                    return null;
                }
            } catch (Exception ex) {
                DTLogger.error("Error executing Python script:", ex);
                return null;
            }
        }

        private void saveResultToModel(String result) {
            try {
                if (result != null && !result.trim().isEmpty()) {
                    String[] values = result.trim().split(",");
                    DTLogger.info("Raw result split into values: " + String.join(", ", values)); // Log para depuración
                    if (values.length >= 4) {
                        try {
                            int temperatura = (int) Math.round(Double.parseDouble(values[0].trim()));
                            int luminosidad = (int) Math.round(Double.parseDouble(values[1].trim()));
                            int encenderHumidificador = Integer.parseInt(values[2].trim());
                            int encenderPurificador = Integer.parseInt(values[3].trim());

                            DTLogger.info("Parsed values: temperatura=" + temperatura + ", luminosidad=" + luminosidad +
                                    ", encenderHumidificador=" + encenderHumidificador + ", encenderPurificador=" + encenderPurificador);

                            for (MObjectState obj : useApi.getObjectsOfClass("AireAcondicionado")) {
                                useApi.setAttribute(obj, "temperatura", temperatura);
                            }
                            for (MObjectState obj : useApi.getObjectsOfClass("Luz")) {
                                useApi.setAttribute(obj, "intensidad", luminosidad);
                            }

                            boolean humidificadorEncendido = encenderHumidificador == 1;
                            for (MObjectState obj : useApi.getObjectsOfClass("ReguladorHumedad")) {
                                useApi.setAttribute(obj, "encendido", humidificadorEncendido);
                                DTLogger.info("ReguladorHumedad actualizado: encendido=" + humidificadorEncendido);
                            }

                            boolean purificadorEncendido = encenderPurificador == 1;
                            for (MObjectState obj : useApi.getObjectsOfClass("PurificadorDeAire")) {
                                useApi.setAttribute(obj, "encendido", purificadorEncendido);
                                DTLogger.info("PurificadorDeAire actualizado: encendido=" + purificadorEncendido);
                            }

                            useApi.updateDerivedValues();
                            DTLogger.info("Model updated with: temperatura=" + temperatura + ", luminosidad=" + luminosidad +
                                    ", encenderHumidificador=" + encenderHumidificador + ", encenderPurificador=" + encenderPurificador);

                        } catch (NumberFormatException e) {
                            DTLogger.error("Could not parse numbers from result string: " + result, e);
                        }
                    } else {
                        DTLogger.error("Invalid result format (expected 4 values, got " + values.length + "): " + result);
                    }
                } else {
                    DTLogger.error("Result is null or empty: " + result);
                }
            } catch (Exception ex) {
                DTLogger.error("Error saving result to USE model:", ex);
            }
        }

        public void stop() {
            running = false;
        }
    }
}