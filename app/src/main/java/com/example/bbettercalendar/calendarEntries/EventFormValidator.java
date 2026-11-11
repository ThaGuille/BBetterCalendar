package com.example.bbettercalendar.calendarEntries;

/**
 * mejora-visual u14: reglas puras del formulario de evento / tarea (sin Android): "Guardar" solo se
 * habilita con titulo y, en un evento, con un fin que no sea anterior al inicio. Las usa
 * {@link AddEventActivity} en cada cambio de titulo / fecha / hora.
 */
public final class EventFormValidator {

    private EventFormValidator() { }

    /** Resultado de validar: ambos flags a false = el formulario se puede guardar. */
    public static final class Result {
        public final boolean titleMissing;
        public final boolean endBeforeStart;

        Result(boolean titleMissing, boolean endBeforeStart) {
            this.titleMissing = titleMissing;
            this.endBeforeStart = endBeforeStart;
        }

        public boolean canSave() {
            return !titleMissing && !endBeforeStart;
        }
    }

    /** Hay titulo cuando no es null ni solo espacios. */
    public static boolean hasTitle(String title) {
        return title != null && !title.trim().isEmpty();
    }

    /** El fin es anterior al inicio (igual al inicio es valido: evento puntual). */
    public static boolean isEndBeforeStart(long startMillis, long endMillis) {
        return endMillis < startMillis;
    }

    /**
     * @param hasEnd {@code true} para un evento (tiene fila "Termina"); una tarea no tiene fin y
     *               {@code endMillis} se ignora.
     */
    public static Result validate(String title, boolean hasEnd, long startMillis, long endMillis) {
        return new Result(!hasTitle(title), hasEnd && isEndBeforeStart(startMillis, endMillis));
    }
}
