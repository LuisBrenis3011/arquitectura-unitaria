package com.brenis.send;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;


@Slf4j
public class Send {

    private static final String RUTA_CONFIG =
            "C:\\Proyecto_V1_arqui\\APPLICATION\\config.properties";

    private static final String KEY_RUTA_LOCAL      = "ruta_local";

    private static final String KEY_RUTA_RED        = "ruta_red";

    private static final String KEY_INTERVALO       = "intervalo_segundos";

    private static final int INTERVALO_DEFAULT_SEG  = 30;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");


    public static void main(String[] args) {
        imprimirBanner();

        Send send = new Send();

        // ── 1. Leer la configuración
        Properties config = send.leerConfiguracion();
        if (config == null) {
            // leerConfiguracion() ya logueó el error específico
            System.err.println("[FATAL] No se puede continuar sin el archivo de configuración.");
            System.exit(1); // Código de salida 1 = error
        }

        // ── 2. Extraer y validar las propiedades
        String rutaLocal = config.getProperty(KEY_RUTA_LOCAL, "").trim();
        String rutaRed   = config.getProperty(KEY_RUTA_RED,   "").trim();
        int intervaloSeg = parsearIntervalo(config.getProperty(KEY_INTERVALO));

        if (!send.validarConfiguracion(rutaLocal, rutaRed)) {
            System.exit(1);
        }

        // ── 3. Registrar ShutdownHook para cierre limpio con Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Send detenido por el usuario (Ctrl+C / señal del sistema).");
            imprimirConsola(" Send detenido.");
        }));

        // ── 4. Iniciar el bucle de transmisión
        send.iniciarBucleDemonio(rutaLocal, rutaRed, intervaloSeg);
    }


    private Properties leerConfiguracion() {
        File archivoConfig = new File(RUTA_CONFIG);

        // Verificar existencia antes de intentar leer
        if (!archivoConfig.exists()) {
            log.error("Archivo de configuración NO encontrado en: {}", RUTA_CONFIG);
            imprimirConsola("✗ ERROR: No se encontró el archivo de configuración.");
            imprimirConsola("  Ruta esperada: " + RUTA_CONFIG);
            imprimirConsola("  Cree el archivo con las claves: ruta_local, ruta_red, intervalo_segundos");
            return null;
        }

        Properties props = new Properties();

        // try-with-resources: cierra el FileInputStream automáticamente
        try (InputStream inputStream = new FileInputStream(archivoConfig)) {
            props.load(inputStream);
            log.info("Configuración cargada exitosamente desde: {}", RUTA_CONFIG);
            imprimirConsola("✔ Configuración leída: " + RUTA_CONFIG);

            // Mostrar las propiedades leídas (útil para depuración)
            imprimirConsola("  → ruta_local       = " + props.getProperty(KEY_RUTA_LOCAL, "[no definida]"));
            imprimirConsola("  → ruta_red         = " + props.getProperty(KEY_RUTA_RED,   "[no definida]"));
            imprimirConsola("  → intervalo_segundos = " + props.getProperty(KEY_INTERVALO, "[usando default: " + INTERVALO_DEFAULT_SEG + "s]"));

            return props;

        } catch (IOException e) {
            log.error("Error al leer el archivo de configuración: {}", e.getMessage(), e);
            imprimirConsola("✗ ERROR leyendo config.properties: " + e.getMessage());
            return null;
        }
    }


    private boolean validarConfiguracion(String rutaLocal, String rutaRed) {
        boolean valido = true;

        // Validar ruta_local
        if (rutaLocal.isEmpty()) {
            log.error("La propiedad '{}' está vacía en config.properties", KEY_RUTA_LOCAL);
            imprimirConsola("✗ ERROR: 'ruta_local' no está definida en config.properties");
            valido = false;
        } else {
            File carpetaLocal = new File(rutaLocal);
            if (!carpetaLocal.exists()) {
                // No es error fatal: CajaApp puede no haber abierto ningún turno aún
                log.warn("La carpeta local '{}' no existe todavía. Se verificará en cada ciclo.", rutaLocal);
                imprimirConsola("⚠  ADVERTENCIA: La carpeta local aún no existe: " + rutaLocal);
                imprimirConsola("   Se verificará en cada ciclo de transmisión.");
            } else if (!carpetaLocal.isDirectory()) {
                log.error("'ruta_local' existe pero no es una carpeta: {}", rutaLocal);
                imprimirConsola("✗ ERROR: 'ruta_local' no es una carpeta válida: " + rutaLocal);
                valido = false;
            }
        }

        // Validar ruta_red (no podemos verificar conectividad aquí,
        // solo que el valor no esté vacío y tenga el formato UNC esperado)
        if (rutaRed.isEmpty()) {
            log.error("La propiedad '{}' está vacía en config.properties", KEY_RUTA_RED);
            imprimirConsola("✗ ERROR: 'ruta_red' no está definida en config.properties");
            valido = false;
        } else if (!rutaRed.startsWith("\\\\")) {
            // Advertencia: no es formato UNC (\\SERVIDOR\RECURSO), pero no bloqueamos
            log.warn("'ruta_red' no parece una ruta UNC (\\\\SERVIDOR\\RECURSO): {}", rutaRed);
            imprimirConsola("⚠  ADVERTENCIA: 'ruta_red' no tiene formato UNC esperado.");
            imprimirConsola("   Formato esperado: \\\\HOSTNAME\\DATOS");
            imprimirConsola("   Valor actual: " + rutaRed);
        }

        return valido;
    }


    private void iniciarBucleDemonio(String rutaLocal, String rutaRed, int intervaloSeg) {
        imprimirConsola("");
        imprimirConsola("══════════════════════════════════════════════════════");
        imprimirConsola("  Send iniciado como servicio de transmisión");
        imprimirConsola("  Origen  : " + rutaLocal);
        imprimirConsola("  Destino : " + rutaRed);
        imprimirConsola("  Ciclo   : cada " + intervaloSeg + " segundos");
        imprimirConsola("  Presione Ctrl+C para detener.");
        imprimirConsola("══════════════════════════════════════════════════════");
        imprimirConsola("");

        int numeroCiclo = 0;

        while (true) { // Bucle demonio — se detiene solo con Ctrl+C
            numeroCiclo++;
            imprimirConsola("─── Ciclo #" + numeroCiclo + " ");

            // Ejecutar la transmisión de este ciclo
            transmitir(rutaLocal, rutaRed);

            // Esperar el intervalo configurado antes del siguiente ciclo
            imprimirConsola("Próxima transmisión en " + intervaloSeg + " segundos...");
            log.info("Ciclo #{} completado. Durmiendo {} segundos.", numeroCiclo, intervaloSeg);

            try {
                // Convertimos segundos a milisegundos para Thread.sleep()
                Thread.sleep((long) intervaloSeg * 1000);
            } catch (InterruptedException e) {
                // InterruptedException ocurre cuando el ShutdownHook interrumpe el hilo
                log.info("Hilo de transmisión interrumpido. Finalizando...");
                Thread.currentThread().interrupt(); // Buena práctica: restaurar el flag
                break; // Salir del bucle limpiamente
            }
        }
    }

    private void transmitir(String rutaLocal, String rutaRed) {

        // Verificar carpeta de origen
        File carpetaOrigen = new File(rutaLocal);

        if (!carpetaOrigen.exists() || !carpetaOrigen.isDirectory()) {
            log.warn("Carpeta local no disponible: {}. Saltando este ciclo.", rutaLocal);
            imprimirConsola("⚠  Carpeta local no encontrada: " + rutaLocal);
            imprimirConsola("   (CajaApp aún no ha creado los archivos de datos)");
            return;
        }

        // Listar solo archivos (no subdirectorios)
        File[] archivos = carpetaOrigen.listFiles(File::isFile);

        if (archivos == null || archivos.length == 0) {
            log.info("No hay archivos en la carpeta local para transmitir: {}", rutaLocal);
            imprimirConsola("⚠  Sin archivos en: " + rutaLocal + " — nada que transmitir.");
            return;
        }

        //  Verificar / crear carpeta de red destino
        File carpetaDestino = new File(rutaRed);

        if (!carpetaDestino.exists()) {
            log.info("La carpeta de red no existe. Intentando crearla: {}", rutaRed);
            imprimirConsola("  Carpeta de red no encontrada. Creando: " + rutaRed);

            boolean creada = carpetaDestino.mkdirs();
            if (!creada) {
                // Esto ocurre si el servidor no está disponible o no hay permisos
                log.error("No se pudo crear la carpeta de red: {}", rutaRed);
                imprimirConsola("✗ ERROR: No se pudo crear la carpeta de red.");
                imprimirConsola("  Verifique que el servidor esté encendido,");
                imprimirConsola("  que la carpeta esté compartida y que tenga permisos de escritura.");
                imprimirConsola("  Ruta intentada: " + rutaRed);
                return; // No podemos transmitir si no existe el destino
            }
            imprimirConsola("  ✔ Carpeta de red creada: " + rutaRed);
        }

        //  Copiar cada archivo
        imprimirConsola("Transmitiendo " + archivos.length + " archivo(s)...");

        int exitosos  = 0;
        int fallidos  = 0;

        for (File archivoOrigen : archivos) {
            // Construir la ruta completa del archivo destino
            // Ejemplo: C:\...\DATA\turno_caja.dat → \\HOSTNAME\DATOS\turno_caja.dat
            Path origen  = archivoOrigen.toPath();
            Path destino = Paths.get(rutaRed, archivoOrigen.getName());

            try {

                Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
                long bytesCopiados = Files.size(origen);

                log.info(" Copiado: {} → {} ({} bytes)", origen.getFileName(), destino, bytesCopiados);
                imprimirConsola(String.format("  ✔ %-30s → %s  (%,d bytes)",
                        archivoOrigen.getName(), rutaRed, bytesCopiados));

                exitosos++;

            } catch (IOException e) {

                log.error("✗ Error copiando '{}': {}", archivoOrigen.getName(), e.getMessage());
                imprimirConsola(String.format("  ✗ Error en %-25s: %s",
                        archivoOrigen.getName(), e.getMessage()));
                fallidos++;
            }
        }

        imprimirConsola(String.format("Transmisión completada: %d exitoso(s), %d fallido(s).",
                exitosos, fallidos));

        if (fallidos > 0) {
            imprimirConsola("⚠  Algunos archivos no pudieron transmitirse. Revisar conectividad.");
        } else {
            imprimirConsola("✔ Todos los archivos transmitidos al servidor central.");
        }

        log.info("Ciclo de transmisión: {} exitosos, {} fallidos.", exitosos, fallidos);
    }


    private static int parsearIntervalo(String valorProperties) {
        if (valorProperties == null || valorProperties.trim().isEmpty()) {
            log.warn("'intervalo_segundos' no definido. Usando default: {}s", INTERVALO_DEFAULT_SEG);
            return INTERVALO_DEFAULT_SEG;
        }
        try {
            int valor = Integer.parseInt(valorProperties.trim());
            if (valor <= 0) {
                log.warn("'intervalo_segundos' debe ser > 0. Usando default: {}s", INTERVALO_DEFAULT_SEG);
                return INTERVALO_DEFAULT_SEG;
            }
            return valor;
        } catch (NumberFormatException e) {
            log.warn("'intervalo_segundos' tiene valor inválido: '{}'. Usando default: {}s",
                    valorProperties, INTERVALO_DEFAULT_SEG);
            return INTERVALO_DEFAULT_SEG;
        }
    }

    private static void imprimirConsola(String mensaje) {
        String timestamp = LocalDateTime.now().format(FMT);
        System.out.println("[" + timestamp + "] " + mensaje);
    }

    private static void imprimirBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║          SEND — Transmisor de Datos POS              ║");
        System.out.println("║  Sistema POS Universitario — Arquitectura Unitaria   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
    }
}

