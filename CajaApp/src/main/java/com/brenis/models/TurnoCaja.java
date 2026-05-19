package com.brenis.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TurnoCaja {


    private int id;
    private String nombreCajero;
    private double montoApertura;
    private double montoCierre;
    private long fecha;
    private boolean estado;
}
