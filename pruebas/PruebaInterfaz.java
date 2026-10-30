package presentacion.pruebas;

import presentacion.adaptadores.grafica.VentanaArchivo;
import presentacion.aplicacion.EditorArchivo;
import presentacion.dominio.Archivo;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class PruebaInterfaz {
    private PruebaInterfaz() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("OMITIDA: la prueba de ventana necesita un entorno gráfico.");
            return;
        }
        Path inicial = Files.createTempFile("memento-inicial-", ".txt");
        Path alternativo = Files.createTempFile("memento-alternativo-", ".txt");
        try {
            Files.writeString(inicial, "Documento inicial", StandardCharsets.UTF_8);
            Files.writeString(alternativo, "Otro documento", StandardCharsets.UTF_8);
            SwingUtilities.invokeAndWait(() -> probarVentana(inicial.toUri().toString(),
                    alternativo.toUri().toString()));
            System.out.println("Prueba de interfaz correcta: aplicar, borrador, deshacer y controles.");
        } finally {
            Files.deleteIfExists(inicial);
            Files.deleteIfExists(alternativo);
        }
    }

    private static void probarVentana(String uriInicial, String uriAlternativa) {
        Archivo archivo = new Archivo(42, "Original", "Ana", "hash-original", uriInicial);
        VentanaArchivo ventana = new VentanaArchivo(archivo, new EditorArchivo(archivo));
        try {
            ventana.setVisible(true);
            JTextField id = campo(ventana, "ID (inmutable)");
            JTextField titulo = campo(ventana, "Título");
            JTextField autor = campo(ventana, "Autor");
            JTextField uri = buscar(ventana, JTextField.class,
                    candidato -> candidato.getText().equals(uriInicial));
            JTextField hash = campo(ventana, "SHA-256 (dato de ejemplo)");
            JButton aplicar = buscar(ventana, JButton.class,
                    candidato -> candidato.getText().equals("Aplicar cambios"));
            JButton deshacer = buscar(ventana, JButton.class,
                    candidato -> candidato.getText().equals("Deshacer"));

            comprobar(!id.isEditable(), "El ID debe ser de solo lectura.");
            comprobar(!aplicar.isEnabled() && !deshacer.isEnabled(), "Estado inicial incorrecto.");
            titulo.setText("Borrador");
            comprobar(archivo.getTitulo().equals("Original"), "El borrador no debe modificar el originador.");
            comprobar(aplicar.isEnabled() && deshacer.isEnabled(), "El borrador debe habilitar los botones.");
            deshacer.doClick();
            comprobar(titulo.getText().equals("Original"), "Deshacer debe descartar el borrador.");
            comprobar(!deshacer.isEnabled(), "Descartar no debe crear un respaldo.");

            titulo.setText("Primera edición");
            autor.setText("Luis");
            uri.setText(uriAlternativa);
            hash.setText("hash-uno");
            aplicar.doClick();
            comprobarEstado(archivo, "Primera edición", "Luis", "hash-uno", uriAlternativa);
            comprobar(!aplicar.isEnabled(), "No debe poder aplicar sin cambios.");

            titulo.setText("Segunda edición");
            autor.setText("Eva");
            aplicar.doClick();
            comprobarEstado(archivo, "Segunda edición", "Eva", "hash-uno", uriAlternativa);
            deshacer.doClick();
            comprobarEstado(archivo, "Primera edición", "Luis", "hash-uno", uriAlternativa);
            comprobar(titulo.getText().equals("Primera edición") && autor.getText().equals("Luis"),
                    "Los campos deben refrescarse después de restaurar.");

            hash.setText("borrador de hash");
            deshacer.doClick();
            comprobar(hash.getText().equals("hash-uno"), "Debe descartar el hash pendiente.");
            comprobar(deshacer.isEnabled(), "Descartar el borrador no debe consumir el historial.");
            deshacer.doClick();
            comprobarEstado(archivo, "Original", "Ana", "hash-original", uriInicial);
            comprobar(uri.getText().equals(uriInicial) && hash.getText().equals("hash-original"),
                    "Deben refrescarse también URI y hash.");
            comprobar(!deshacer.isEnabled(), "El historial agotado debe deshabilitar Deshacer.");

            titulo.setText("Nueva edición");
            aplicar.doClick();
            deshacer.doClick();
            comprobarEstado(archivo, "Original", "Ana", "hash-original", uriInicial);
        } finally {
            ventana.dispose();
        }
    }

    private static JTextField campo(Container raiz, String nombre) {
        JLabel etiqueta = buscar(raiz, JLabel.class, candidato -> candidato.getText().equals(nombre));
        return (JTextField) etiqueta.getLabelFor();
    }

    private static <T extends Component> T buscar(Container raiz, Class<T> tipo, Predicate<T> condicion) {
        T encontrado = buscarOpcional(raiz, tipo, condicion);
        if (encontrado == null) {
            throw new AssertionError("No se encontró el control: " + tipo.getSimpleName());
        }
        return encontrado;
    }

    private static <T extends Component> T buscarOpcional(Container raiz, Class<T> tipo, Predicate<T> condicion) {
        for (Component componente : raiz.getComponents()) {
            if (tipo.isInstance(componente) && condicion.test(tipo.cast(componente))) {
                return tipo.cast(componente);
            }
            if (componente instanceof Container contenedor) {
                T encontrado = buscarOpcional(contenedor, tipo, condicion);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private static void comprobarEstado(Archivo archivo, String titulo, String autor, String hash, String uri) {
        comprobar(archivo.getId() == 42, "El ID debe conservarse.");
        comprobar(archivo.getTitulo().equals(titulo), "Título incorrecto.");
        comprobar(archivo.getAutor().equals(autor), "Autor incorrecto.");
        comprobar(archivo.getHashSHA256().equals(hash), "Hash incorrecto.");
        comprobar(archivo.getURIAlmacenamiento().equals(uri), "URI incorrecta.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
