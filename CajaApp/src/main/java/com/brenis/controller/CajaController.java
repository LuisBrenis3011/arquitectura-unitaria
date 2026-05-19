package com.brenis.controller;

import com.brenis.models.TurnoCaja;
import com.brenis.repositories.TurnoRepository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;


@Slf4j
public class CajaController implements Initializable {

    @FXML private VBox panelAbrirCaja;

    @FXML private TextField txtNombreCajero;

    @FXML private TextField txtMontoApertura;

    @FXML private Button btnAbrirCaja;

    @FXML private VBox panelCerrarCaja;

    @FXML private TextField txtIdTurno;

    @FXML private TextField txtMontoCierre;

    @FXML private Button btnCerrarCaja;

    @FXML private Label lblEstadoTurno;

    @FXML private Label lblIdTurnoActivo;

    @FXML private TextArea txtAreaLog;

    @FXML private TableView<TurnoCaja> tablaHistorial;

    @FXML private TableColumn<TurnoCaja, Integer> colId;

    @FXML private TableColumn<TurnoCaja, String> colNombre;

    @FXML private TableColumn<TurnoCaja, Double> colMontoApertura;

    @FXML private TableColumn<TurnoCaja, Double> colMontoCierre;

    @FXML private TableColumn<TurnoCaja, Long> colFecha;

    @FXML private TableColumn<TurnoCaja, Boolean> colEstado;

    private TurnoRepository turnoRepository;

    private TurnoCaja turnoActivoActual;

    // Formateadores auxiliares
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final NumberFormat FORMATO_MONEDA =
            NumberFormat.getCurrencyInstance(new Locale("es", "PE"));


    @Override
    public void initialize(URL url, ResourceBundle bundle) {
        log.info("Inicializando CajaController...");

        this.turnoRepository = new TurnoRepository();

        //  Inicializar los archivos binarios (.dat y .idx)
        try {
            turnoRepository.inicializar();
            registrarLog("Sistema iniciado. Archivos de datos verificados.");
        } catch (IOException e) {
            log.error("Error al inicializar el repositorio: {}", e.getMessage(), e);
            mostrarAlerta(Alert.AlertType.ERROR,
                    "Error de Inicialización",
                    "No se pudieron crear los archivos de datos.",
                    "Verifique permisos en: C:\\Proyecto_V1_arqui\\DATA\\\n\n"
                            + "Detalle: " + e.getMessage());
        }

        // Configurar la TableView con PropertyValueFactory
        //    PropertyValueFactory usa reflexión para llamar los getters de Lombok.
        //    Ejemplo: "id" → llama turno.getId() gracias a @Data de Lombok.
        configurarColumnas();

        // Configurar validación numérica en los campos de monto
        configurarValidacionNumerica(txtMontoApertura);
        configurarValidacionNumerica(txtMontoCierre);
        configurarValidacionSoloNumeros(txtIdTurno);

        // Cargar el historial inicial
        actualizarTablaHistorial();

        // Estado inicial de la UI
        actualizarEstadoUI(false);

        log.info("CajaController inicializado correctamente.");
    }

    @FXML
    private void handleAbrirCaja() {
        log.info("Acción: Abrir Caja");

        String nombreCajero = txtNombreCajero.getText().trim();
        if (nombreCajero.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Campo Requerido", "Nombre del cajero vacío",
                    "Por favor ingrese el nombre del cajero.");
            txtNombreCajero.requestFocus();
            return;
        }
        if (nombreCajero.length() > 100) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Nombre Muy Largo", "Límite: 100 caracteres",
                    "El nombre no puede superar 100 caracteres (restricción del campo char[100]).\n"
                            + "Caracteres actuales: " + nombreCajero.length());
            return;
        }

        double montoApertura;
        try {
            montoApertura = Double.parseDouble(
                    txtMontoApertura.getText().trim().replace(",", "."));
            if (montoApertura < 0) {
                mostrarAlerta(Alert.AlertType.WARNING,
                        "Monto Inválido", "El monto no puede ser negativo", "");
                return;
            }
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Formato Incorrecto", "Monto de apertura inválido",
                    "Ingrese un número válido (ej: 500.00)");
            txtMontoApertura.requestFocus();
            return;
        }

        boolean confirmar = mostrarConfirmacion(
                "Confirmar Apertura de Caja",
                "¿Abrir caja para: " + nombreCajero + "?",
                "Monto inicial: " + FORMATO_MONEDA.format(montoApertura));
        if (!confirmar) return;

        try {
            TurnoCaja nuevoTurno = turnoRepository.abrirCaja(nombreCajero, montoApertura);
            turnoActivoActual = nuevoTurno;

            actualizarEstadoUI(true);
            txtIdTurno.setText(String.valueOf(nuevoTurno.getId()));
            limpiarCamposApertura();

            String mensaje = String.format(
                    "[CAJA ABIERTA] ID Turno: %d | Cajero: %s | Monto: %s | Hora: %s",
                    nuevoTurno.getId(),
                    nuevoTurno.getNombreCajero(),
                    FORMATO_MONEDA.format(nuevoTurno.getMontoApertura()),
                    formatearFecha(nuevoTurno.getFecha())
            );
            registrarLog(mensaje);
            actualizarTablaHistorial();

            mostrarAlerta(Alert.AlertType.INFORMATION,
                    "Caja Abierta",
                    "Turno #" + nuevoTurno.getId() + " iniciado correctamente.",
                    "Cajero: " + nuevoTurno.getNombreCajero()
                            + "\nMonto apertura: " + FORMATO_MONEDA.format(nuevoTurno.getMontoApertura()));

        } catch (IOException e) {
            log.error("Error al abrir caja: {}", e.getMessage(), e);
            registrarLog("[ERROR] No se pudo abrir caja: " + e.getMessage());
            mostrarAlerta(Alert.AlertType.ERROR,
                    "Error de I/O",
                    "No se pudo registrar la apertura de caja.",
                    "Detalle técnico: " + e.getMessage());
        }
    }


    @FXML
    private void handleCerrarCaja() {
        log.info("Acción: Cerrar Caja");

        // ── Validar ID del turno
        int idTurno;
        try {
            idTurno = Integer.parseInt(txtIdTurno.getText().trim());
            if (idTurno <= 0) {
                mostrarAlerta(Alert.AlertType.WARNING,
                        "ID Inválido", "El ID debe ser mayor a 0", "");
                return;
            }
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "ID Inválido", "Ingrese un número entero válido como ID.", "");
            txtIdTurno.requestFocus();
            return;
        }

        // ── Validar monto de cierre
        double montoCierre;
        try {
            montoCierre = Double.parseDouble(
                    txtMontoCierre.getText().trim().replace(",", "."));
            if (montoCierre < 0) {
                mostrarAlerta(Alert.AlertType.WARNING,
                        "Monto Inválido", "El monto de cierre no puede ser negativo", "");
                return;
            }
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Formato Incorrecto", "Monto de cierre inválido",
                    "Ingrese un número válido (ej: 750.50)");
            txtMontoCierre.requestFocus();
            return;
        }

        // ── Verificar que el turno existe y está abierto
        try {
            Optional<TurnoCaja> turnoOpt = turnoRepository.buscarPorId(idTurno);
            if (turnoOpt.isEmpty()) {
                mostrarAlerta(Alert.AlertType.WARNING,
                        "Turno No Encontrado",
                        "No existe un turno con ID: " + idTurno,
                        "Verifique el ID e intente nuevamente.");
                return;
            }
            TurnoCaja turno = turnoOpt.get();
            if (!turno.isEstado()) {
                mostrarAlerta(Alert.AlertType.WARNING,
                        "Turno Ya Cerrado",
                        "El turno #" + idTurno + " ya fue cerrado anteriormente.",
                        "Cajero: " + turno.getNombreCajero());
                return;
            }
        } catch (IOException e) {
            log.error("Error al verificar el turno: {}", e.getMessage(), e);
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Lectura",
                    "No se pudo leer el turno.", e.getMessage());
            return;
        }

        // ── Confirmar cierre
        boolean confirmar = mostrarConfirmacion(
                "Confirmar Cierre de Caja",
                "¿Cerrar el turno #" + idTurno + "?",
                "Monto de cierre: " + FORMATO_MONEDA.format(montoCierre)
                        + "\n\nEsta acción actualizará directamente los bytes del archivo binario.");
        if (!confirmar) return;

        // ── Llamada al repositorio
        try {
            boolean exitoso = turnoRepository.cerrarCaja(idTurno, montoCierre);

            if (exitoso) {
                turnoActivoActual = null;
                actualizarEstadoUI(false);
                limpiarCamposCierre();

                String mensaje = String.format(
                        "[CAJA CERRADA] ID Turno: %d | Monto Cierre: %s | Hora: %s",
                        idTurno,
                        FORMATO_MONEDA.format(montoCierre),
                        formatearFecha(System.currentTimeMillis())
                );
                registrarLog(mensaje);
                actualizarTablaHistorial();

                mostrarAlerta(Alert.AlertType.INFORMATION,
                        "Caja Cerrada",
                        "Turno #" + idTurno + " cerrado correctamente.",
                        "Monto de cierre: " + FORMATO_MONEDA.format(montoCierre)
                                + "\n\nLos datos fueron actualizados en el archivo binario.");
            }
        } catch (IllegalStateException e) {
            // Lanzada por TurnoRepository si el turno ya estaba cerrado
            registrarLog("[ADVERTENCIA] " + e.getMessage());
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Operación Inválida", e.getMessage(), "");
        } catch (IOException e) {
            log.error("Error al cerrar caja: {}", e.getMessage(), e);
            registrarLog("[ERROR] No se pudo cerrar caja: " + e.getMessage());
            mostrarAlerta(Alert.AlertType.ERROR,
                    "Error de I/O",
                    "No se pudo registrar el cierre de caja.",
                    "Detalle técnico: " + e.getMessage());
        }
    }


    @FXML
    private void handleRefrescarHistorial() {
        actualizarTablaHistorial();
        registrarLog("Historial actualizado desde archivo binario.");
    }

    @FXML
    private void handleDiagnostico() {
        // Redirigir la salida estándar al TextArea
        turnoRepository.diagnosticar();
        registrarLog("──── DIAGNÓSTICO EJECUTADO ────");
        registrarLog("RECORD_SIZE     = " + TurnoRepository.RECORD_SIZE + " bytes");
        registrarLog("INDEX_RECORD_SIZE = " + TurnoRepository.INDEX_RECORD_SIZE + " bytes");
        registrarLog("Ver consola para el mapa completo de bytes.");
    }


    private void configurarColumnas() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombreCajero"));
        colMontoApertura.setCellValueFactory(new PropertyValueFactory<>("montoApertura"));
        colMontoCierre.setCellValueFactory(new PropertyValueFactory<>("montoCierre"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        // Formateador de montos en la tabla
        colMontoApertura.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double monto, boolean empty) {
                super.updateItem(monto, empty);
                setText((empty || monto == null) ? null : FORMATO_MONEDA.format(monto));
            }
        });

        colMontoCierre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double monto, boolean empty) {
                super.updateItem(monto, empty);
                setText((empty || monto == null) ? null : FORMATO_MONEDA.format(monto));
            }
        });

        // Formateador de fechas: convierte long (epoch ms) a texto legible
        colFecha.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long epoch, boolean empty) {
                super.updateItem(epoch, empty);
                setText((empty || epoch == null || epoch == 0) ? null : formatearFecha(epoch));
            }
        });

        // Formateador de estado: boolean → texto con estilo visual
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setText(null);
                    setStyle("");
                } else if (estado) {
                    setText("● ABIERTO");
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    setText("○ CERRADO");
                    setStyle("-fx-text-fill: #e74c3c;");
                }
            }
        });

        log.debug("Columnas de la tabla configuradas.");
    }


    private void actualizarTablaHistorial() {
        try {
            List<TurnoCaja> turnos = turnoRepository.listarTodos();

            // ObservableList: la TableView se actualiza automáticamente al modificarla
            ObservableList<TurnoCaja> datos = FXCollections.observableArrayList(turnos);

            // Siempre actualizar la UI desde el hilo de JavaFX
            Platform.runLater(() -> tablaHistorial.setItems(datos));

            log.debug("Tabla historial actualizada: {} registros.", turnos.size());
        } catch (IOException e) {
            log.error("Error al leer historial: {}", e.getMessage(), e);
            registrarLog("[ERROR] No se pudo cargar el historial: " + e.getMessage());
        }
    }

    private void actualizarEstadoUI(boolean hayTurnoAbierto) {
        // Sección Abrir Caja
        btnAbrirCaja.setDisable(hayTurnoAbierto);
        txtNombreCajero.setDisable(hayTurnoAbierto);
        txtMontoApertura.setDisable(hayTurnoAbierto);

        // Sección Cerrar Caja
        btnCerrarCaja.setDisable(!hayTurnoAbierto);
        txtMontoCierre.setDisable(!hayTurnoAbierto);

        // Etiqueta de estado
        if (hayTurnoAbierto && turnoActivoActual != null) {
            lblEstadoTurno.setText("● CAJA ABIERTA");
            lblEstadoTurno.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 14px;");
            lblIdTurnoActivo.setText("Turno activo: #" + turnoActivoActual.getId()
                    + " — " + turnoActivoActual.getNombreCajero());
        } else {
            lblEstadoTurno.setText("○ CAJA CERRADA");
            lblEstadoTurno.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");
            lblIdTurnoActivo.setText("Sin turno activo en esta sesión");
        }
    }


    private void configurarValidacionNumerica(TextField campo) {
        campo.textProperty().addListener((observable, valorAnterior, valorNuevo) -> {
            // Permite: dígitos, un punto decimal y signo negativo al inicio
            if (!valorNuevo.matches("-?\\d*(\\.\\d*)?")) {
                campo.setText(valorAnterior);
            }
        });
    }


    private void configurarValidacionSoloNumeros(TextField campo) {
        campo.textProperty().addListener((observable, valorAnterior, valorNuevo) -> {
            if (!valorNuevo.matches("\\d*")) {
                campo.setText(valorAnterior);
            }
        });
    }

    private void registrarLog(String mensaje) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String linea = "[" + timestamp + "] " + mensaje + "\n";
        Platform.runLater(() -> {
            txtAreaLog.appendText(linea);
            // Hacer scroll automático al final
            txtAreaLog.setScrollTop(Double.MAX_VALUE);
        });
        log.info(mensaje);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo,
                               String encabezado, String contenido) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(encabezado);
        alerta.setContentText(contenido.isEmpty() ? null : contenido);
        alerta.showAndWait();
    }

    private boolean mostrarConfirmacion(String titulo, String encabezado, String contenido) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle(titulo);
        confirmacion.setHeaderText(encabezado);
        confirmacion.setContentText(contenido);

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        return resultado.isPresent() && resultado.get() == ButtonType.OK;
    }

    private String formatearFecha(long epochMs) {
        LocalDateTime fecha = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
        return fecha.format(FORMATO_FECHA);
    }

    private void limpiarCamposApertura() {
        txtNombreCajero.clear();
        txtMontoApertura.clear();
    }

    private void limpiarCamposCierre() {
        txtIdTurno.clear();
        txtMontoCierre.clear();
    }
}

