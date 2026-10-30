package aplicacion;

import dominio.Archivo;
import dominio.ArchivoMemento;

import java.util.ArrayDeque;
import java.util.Deque;

//caretaker
public class EditorArchivo {
    private final Archivo archivo;
    private final Deque<ArchivoMemento> historial = new ArrayDeque<>();

    public EditorArchivo(Archivo archivo) {
        this.archivo = archivo;
    }

    public void guardar() {
        historial.push(archivo.guardarEstado());
    }

    public boolean deshacer() {
        if (historial.isEmpty()) {
            return false;
        }

        archivo.restaurarEstado(historial.pop());
        return true;
    }
}
