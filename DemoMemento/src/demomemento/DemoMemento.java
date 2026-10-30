package demomemento;

import adaptadores.grafica.VentanaArchivo;
import aplicacion.EditorArchivo;
import dominio.Archivo;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class DemoMemento {
    private DemoMemento() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("La demo necesita un entorno gráfico para mostrar la ventana Swing.");
            return;
        }
        Path ruta = Path.of(args.length > 0 ? args[0] : "src\\recursos\\documento-demo.txt")
                .toAbsolutePath().normalize();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) {
                // La apariencia de Swing sigue disponible si falla la del sistema.
            }
            Archivo archivo = new Archivo(ruta.toUri().toString());
            archivo.setTitulo("Documento de demostración");
            archivo.setAutor("Ana García");
            EditorArchivo editor = new EditorArchivo(archivo);
            new VentanaArchivo(archivo, editor).setVisible(true);
        });
    }
}