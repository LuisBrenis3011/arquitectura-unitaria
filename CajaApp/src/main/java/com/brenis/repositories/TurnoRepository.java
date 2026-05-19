package com.brenis.repositories;


import com.brenis.models.TurnoCaja;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
public class TurnoRepository {


    private static final int SIZE_INT    = 4;
    private static final int SIZE_NOMBRE = 100 * 2; // = 200 bytes
    private static final int SIZE_DOUBLE = 8;
    private static final int SIZE_LONG   = 8;
    private static final int SIZE_BOOL   = 1;
    public static final int RECORD_SIZE = SIZE_INT    // id
            + SIZE_NOMBRE // nombreCajero
            + SIZE_DOUBLE // montoApertura
            + SIZE_DOUBLE // montoCierre
            + SIZE_LONG   // fecha
            + SIZE_BOOL;  // estado
    // Verificación en tiempo de carga de clase
    static {
        assert RECORD_SIZE == 229 : "ERROR CRÍTICO: El tamaño del registro no es 229 bytes. Revisar constantes.";
    }

    public static final int INDEX_RECORD_SIZE = SIZE_INT + SIZE_LONG; // = 12 bytes

    private static final int OFFSET_ID             = 0;

    /** Offset de `nombreCajero`:  4 bytes (justo después de id=4bytes) */
    private static final int OFFSET_NOMBRE         = OFFSET_ID + SIZE_INT;           // = 4

    /** Offset de `montoApertura`: 204 bytes (id=4 + nombre=200) */
    private static final int OFFSET_MONTO_APERTURA = OFFSET_NOMBRE + SIZE_NOMBRE;    // = 204

    /** Offset de `montoCierre`:   212 bytes (montoApertura=204 + double=8) */
    private static final int OFFSET_MONTO_CIERRE   = OFFSET_MONTO_APERTURA + SIZE_DOUBLE; // = 212

    /** Offset de `fecha`:         220 bytes (montoCierre=212 + double=8) */
    private static final int OFFSET_FECHA          = OFFSET_MONTO_CIERRE + SIZE_DOUBLE;   // = 220

    /** Offset de `estado`:        228 bytes (fecha=220 + long=8) */
    private static final int OFFSET_ESTADO         = OFFSET_FECHA + SIZE_LONG;            // = 228

    private static final String RUTA_DAT = "C:\\Proyecto_V1_arqui\\DATA\\turno_caja.dat";

    /** Archivo índice: entradas de 12 bytes cada una (id + posición física) */
    private static final String RUTA_IDX = "C:\\Proyecto_V1_arqui\\DATA\\turno_caja.idx";


    public void inicializar() throws IOException {
        File archivoDat = new File(RUTA_DAT);
        File archivoIdx = new File(RUTA_IDX);

        // Crear estructura de directorios si no existe
        File directorio = archivoDat.getParentFile();
        if (!directorio.exists()) {
            boolean creado = directorio.mkdirs();
            if (creado) {
                log.info("Directorio de datos creado: {}", directorio.getAbsolutePath());
            }
        }

        if (!archivoDat.exists()) {
            boolean creado = archivoDat.createNewFile();
            if (creado) {
                log.info("Archivo maestro creado: {}", RUTA_DAT);
            }
        }

        if (!archivoIdx.exists()) {
            boolean creado = archivoIdx.createNewFile();
            if (creado) {
                log.info("Archivo índice creado: {}", RUTA_IDX);
            }
        }

        log.info("TurnoRepository inicializado. RECORD_SIZE={} bytes, INDEX_RECORD_SIZE={} bytes",
                RECORD_SIZE, INDEX_RECORD_SIZE);
    }

    public TurnoCaja abrirCaja(String nombreCajero, double montoApertura) throws IOException {

        // Construir el objeto del turno
        TurnoCaja turno = TurnoCaja.builder()
                .nombreCajero(nombreCajero)
                .montoApertura(montoApertura)
                .montoCierre(0.0)          // Sin cierre todavía
                .fecha(System.currentTimeMillis()) // Timestamp de apertura
                .estado(true)              // ABIERTO = true
                .build();

        try (RandomAccessFile rafDat = new RandomAccessFile(RUTA_DAT, "rw");
             RandomAccessFile rafIdx = new RandomAccessFile(RUTA_IDX, "rw")) {

            long totalEntradas = rafIdx.length() / INDEX_RECORD_SIZE;
            int nuevoId = (int) totalEntradas + 1;
            turno.setId(nuevoId);

            log.info("Abriendo caja → ID generado: {}, Cajero: '{}'", nuevoId, nombreCajero);

            long posicionFisica = rafDat.length();

            rafDat.seek(posicionFisica);
            escribirRegistro(rafDat, turno);

            log.debug("Registro escrito en .dat → posición física: {} bytes "
                            + "(registro #{} × {} bytes/registro)",
                    posicionFisica, nuevoId - 1, RECORD_SIZE);

            rafIdx.seek(rafIdx.length());
            rafIdx.writeInt(nuevoId);         // 4 bytes: el ID del registro
            rafIdx.writeLong(posicionFisica); // 8 bytes: dónde está en el .dat

            log.debug("Entrada de índice escrita → id={}, posicionFisica={}", nuevoId, posicionFisica);
        }

        log.info("✔ Caja ABIERTA exitosamente. ID Turno: {}", turno.getId());
        return turno;
    }

    public boolean cerrarCaja(int id, double montoCierre) throws IOException {

        // ── PASO 1: Buscar la posición física en el archivo .idx ─────────────
        Optional<Long> posicionOpt = buscarPosicionEnIndice(id);

        if (posicionOpt.isEmpty()) {
            log.warn("No se encontró el turno con ID={} en el archivo índice.", id);
            return false;
        }

        long posicionFisica = posicionOpt.get();
        log.info("Cerrando caja → ID: {}, posición física en .dat: {} bytes", id, posicionFisica);

        try (RandomAccessFile rafDat = new RandomAccessFile(RUTA_DAT, "rw")) {

            rafDat.seek(posicionFisica + OFFSET_ESTADO);
            boolean estadoActual = rafDat.readBoolean();

            if (!estadoActual) {
                String msg = "La caja con ID=" + id + " ya fue cerrada anteriormente.";
                log.warn(msg);
                throw new IllegalStateException(msg);
            }


            long posMontoCierre = posicionFisica + OFFSET_MONTO_CIERRE;
            rafDat.seek(posMontoCierre);
            rafDat.writeDouble(montoCierre); // Escribe 8 bytes (double)

            log.debug("montoCierre actualizado → seek({} + {}) = seek({}), valor={}",
                    posicionFisica, OFFSET_MONTO_CIERRE, posMontoCierre, montoCierre);


            long posEstado = posicionFisica + OFFSET_ESTADO;
            rafDat.seek(posEstado);
            rafDat.writeBoolean(false); // Escribe 1 byte: 0x00 (false = CERRADO)

            log.debug("estado actualizado → seek({} + {}) = seek({}), valor=false",
                    posicionFisica, OFFSET_ESTADO, posEstado);
        }

        log.info("✔ Caja CERRADA exitosamente. ID Turno: {}, Monto Cierre: {}", id, montoCierre);
        return true;
    }

    public Optional<TurnoCaja> buscarPorId(int id) throws IOException {

        Optional<Long> posicionOpt = buscarPosicionEnIndice(id);

        if (posicionOpt.isEmpty()) {
            log.warn("buscarPorId: No se encontró ID={}", id);
            return Optional.empty();
        }

        long posicionFisica = posicionOpt.get();

        try (RandomAccessFile rafDat = new RandomAccessFile(RUTA_DAT, "r")) {
            rafDat.seek(posicionFisica);
            TurnoCaja turno = leerRegistro(rafDat);
            log.debug("buscarPorId({}): registro leído desde posición {}", id, posicionFisica);
            return Optional.of(turno);
        }
    }


    public List<TurnoCaja> listarTodos() throws IOException {
        List<TurnoCaja> lista = new ArrayList<>();

        try (RandomAccessFile rafDat = new RandomAccessFile(RUTA_DAT, "r")) {
            long totalRegistros = rafDat.length() / RECORD_SIZE;
            log.info("listarTodos: {} registros encontrados en el archivo maestro.", totalRegistros);

            for (long i = 0; i < totalRegistros; i++) {
                // FÓRMULA: posicion = numeroRegistro × RECORD_SIZE
                // Registro 0 → seek(0)
                // Registro 1 → seek(229)
                // Registro 2 → seek(458)
                // Registro N → seek(N × 229)
                rafDat.seek(i * RECORD_SIZE);
                lista.add(leerRegistro(rafDat));
            }
        }
        return lista;
    }


    private void escribirRegistro(RandomAccessFile raf, TurnoCaja turno) throws IOException {

        // Campo 1: id → 4 bytes
        raf.writeInt(turno.getId());

        // Campo 2: nombreCajero → 200 bytes (100 chars × 2 bytes/char)
        // Truncamos si supera 100 chars; rellenamos con '\0' si es más corto.
        String nombre = turno.getNombreCajero() != null ? turno.getNombreCajero() : "";
        int longitudMaxima = 100;
        for (int i = 0; i < longitudMaxima; i++) {
            if (i < nombre.length()) {
                raf.writeChar(nombre.charAt(i)); // 2 bytes por carácter
            } else {
                raf.writeChar('\0'); // Padding: 2 bytes de relleno nulo
            }
        }

        // Campo 3: montoApertura → 8 bytes
        raf.writeDouble(turno.getMontoApertura());

        // Campo 4: montoCierre → 8 bytes
        raf.writeDouble(turno.getMontoCierre());

        // Campo 5: fecha → 8 bytes
        raf.writeLong(turno.getFecha());

        // Campo 6: estado → 1 byte (true=1, false=0)
        raf.writeBoolean(turno.isEstado());

    }


    private TurnoCaja leerRegistro(RandomAccessFile raf) throws IOException {

        // Campo 1: id → 4 bytes
        int id = raf.readInt();

        // Campo 2: nombreCajero → 200 bytes (leemos 100 chars de 2 bytes c/u)
        // Construimos el String eliminando los caracteres nulos de padding.
        StringBuilder sbNombre = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            char c = raf.readChar(); // Lee 2 bytes y devuelve un char
            if (c != '\0') {        // Ignorar el relleno nulo
                sbNombre.append(c);
            }
        }
        String nombreCajero = sbNombre.toString();

        // Campo 3: montoApertura → 8 bytes
        double montoApertura = raf.readDouble();

        // Campo 4: montoCierre → 8 bytes
        double montoCierre = raf.readDouble();

        // Campo 5: fecha → 8 bytes
        long fecha = raf.readLong();

        // Campo 6: estado → 1 byte
        boolean estado = raf.readBoolean();

        return TurnoCaja.builder()
                .id(id)
                .nombreCajero(nombreCajero)
                .montoApertura(montoApertura)
                .montoCierre(montoCierre)
                .fecha(fecha)
                .estado(estado)
                .build();
    }


    private Optional<Long> buscarPosicionEnIndice(int id) throws IOException {

        try (RandomAccessFile rafIdx = new RandomAccessFile(RUTA_IDX, "r")) {

            long totalEntradas = rafIdx.length() / INDEX_RECORD_SIZE;
            log.debug("buscarPosicionEnIndice({}): escaneando {} entradas del índice.", id, totalEntradas);

            for (long i = 0; i < totalEntradas; i++) {
                // Saltar a la entrada i-ésima del índice
                // FÓRMULA: posicion = i × INDEX_RECORD_SIZE = i × 12
                rafIdx.seek(i * INDEX_RECORD_SIZE);

                int idAlmacenado     = rafIdx.readInt();  // Lee 4 bytes: el ID
                long posicionFisica  = rafIdx.readLong(); // Lee 8 bytes: la posición en .dat

                if (idAlmacenado == id) {
                    log.debug("ID={} encontrado en índice[{}] → posicionFisica={} bytes", id, i, posicionFisica);
                    return Optional.of(posicionFisica);
                }
            }
        }

        return Optional.empty(); // No encontrado
    }


    public void diagnosticar() {
        System.out.println(" ");
        System.out.println("  DIAGNÓSTICO TurnoRepository");
        System.out.println(" ");
        System.out.printf("  Archivo DAT : %s%n", RUTA_DAT);
        System.out.printf("  Archivo IDX : %s%n", RUTA_IDX);
        System.out.println();
        System.out.println("  MAPA DE BYTES (registro maestro):");
        System.out.printf("  %-18s | offset=%3d | size=%3d bytes%n", "id (int)",            OFFSET_ID,             SIZE_INT);
        System.out.printf("  %-18s | offset=%3d | size=%3d bytes (100 chars × 2 bytes/char)%n", "nombreCajero",    OFFSET_NOMBRE,         SIZE_NOMBRE);
        System.out.printf("  %-18s | offset=%3d | size=%3d bytes%n", "montoApertura(double)",OFFSET_MONTO_APERTURA, SIZE_DOUBLE);
        System.out.printf("  %-18s | offset=%3d | size=%3d bytes%n", "montoCierre (double)", OFFSET_MONTO_CIERRE,   SIZE_DOUBLE);
        System.out.printf("  %-18s | offset=%3d | size=%3d bytes%n", "fecha (long)",         OFFSET_FECHA,          SIZE_LONG);
        System.out.printf("  %-18s | offset=%3d | size=%3d bytes%n", "estado (boolean)",     OFFSET_ESTADO,         SIZE_BOOL);
        System.out.println("  ──────────────────────────────────────────");
        System.out.printf("  TOTAL REGISTRO     : %d bytes %s%n",
                RECORD_SIZE, RECORD_SIZE == 229 ? "✓" : "✗ ERROR");
        System.out.printf("  TOTAL ENTRADA IDX  : %d bytes %s%n",
                INDEX_RECORD_SIZE, INDEX_RECORD_SIZE == 12 ? "✓" : "✗ ERROR");
        System.out.println();

        // Mostrar tamaño actual de los archivos
        File dat = new File(RUTA_DAT);
        File idx = new File(RUTA_IDX);
        if (dat.exists()) {
            long totalReg = dat.length() / RECORD_SIZE;
            System.out.printf("  Tamaño .dat: %d bytes → %d registros%n", dat.length(), totalReg);
        } else {
            System.out.println("  Archivo .dat: NO EXISTE todavía");
        }
        if (idx.exists()) {
            long totalIdx = idx.length() / INDEX_RECORD_SIZE;
            System.out.printf("  Tamaño .idx: %d bytes → %d entradas de índice%n", idx.length(), totalIdx);
        } else {
            System.out.println("  Archivo .idx: NO EXISTE todavía");
        }

    }
}

