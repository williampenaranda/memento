package presentacion.pruebas;

import presentacion.adaptadores.grafica.VistaPreviaArchivo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Pruebas ejecutables sin dependencias ni ventanas. No requieren habilitar -ea.
 * Ejecutar con java -Djava.awt.headless=true presentacion.pruebas.PruebaVistaPrevia
 * usando el directorio de clases correspondiente en el classpath.
 *
 * <p>Solo se inspecciona la API pública y el árbol de componentes. Las tareas del
 * EDT son breves: nunca esperan cargas, futuros ni temporizadores. El hilo de
 * pruebas espera como máximo 5 segundos por tarea del EDT y 8 por carga.
 */
public final class PruebaVistaPrevia {
    private static final int LIMITE_TEXTO = 1024 * 1024;
    private static final long ESPERA_CARGA = TimeUnit.SECONDS.toNanos(8);
    private static final String TEXTO_UTF8 = "Hola, España: áéíóú, ñ, 日本語, 😀\n\tSegunda línea\r\n";

    private final VistaPreviaArchivo vista;
    private final Path directorio;
    private int comprobaciones;

    private PruebaVistaPrevia(VistaPreviaArchivo vista, Path directorio) {
        this.vista = vista;
        this.directorio = directorio;
    }

    public static void main(String[] args) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Ejecute main fuera del EDT.");
        }
        Path directorio = Files.createTempDirectory("prueba-vista-previa-");
        VistaPreviaArchivo vista = null;
        PruebaVistaPrevia prueba = null;
        try {
            vista = enEdt(() -> {
                VistaPreviaArchivo panel = new VistaPreviaArchivo();
                panel.addNotify();
                return panel;
            });
            prueba = new PruebaVistaPrevia(vista, directorio);
            prueba.probarTexto();
            prueba.probarHtmlLiteral();
            prueba.probarImagen();
            prueba.probarFormatosYUri();
            prueba.probarErroresDeTexto();
            prueba.probarLimitesDeTexto();
            prueba.probarLimitesDeImagen();
            prueba.probarSolicitudesRapidas();
            prueba.probarCicloDeVida();
            prueba.probarExigenciaDeEdt();
        } finally {
            try {
                if (vista != null) {
                    VistaPreviaArchivo panel = vista;
                    enEdt(() -> {
                        panel.removeNotify();
                        return null;
                    });
                }
            } finally {
                borrarDirectorio(directorio);
            }
        }
        System.out.println("PruebaVistaPrevia: " + prueba.comprobaciones
                + " comprobaciones correctas; temporales eliminados y panel retirado.");
    }

    private void probarTexto() throws Exception {
        Path archivo = escribir("espacio á.TXT", TEXTO_UTF8.getBytes(StandardCharsets.UTF_8));
        Instantanea actual = cargar(archivo.toUri().toString());
        comprobarTexto(actual, TEXTO_UTF8);
        comprobar(!actual.editable(), "El texto debe ser de solo lectura.");
        comprobar(Font.MONOSPACED.equals(actual.fuente()), "Se esperaba una fuente monoespaciada.");
        comprobar(Files.readString(archivo).equals(TEXTO_UTF8), "La vista no debe modificar el archivo.");

        Path bom = escribir("bom.md", ("\uFEFF" + TEXTO_UTF8).getBytes(StandardCharsets.UTF_8));
        comprobarTexto(cargar(bom.toUri().toString()), TEXTO_UTF8);
        comprobarTexto(cargar(escribir("vacio.txt", new byte[0]).toUri().toString()), "");
    }

    private void probarHtmlLiteral() throws Exception {
        String html = "<html><b>No interpretar</b><img src=\"https://example.invalid/imagen\"></html>";
        Path archivo = escribir("literal.html", html.getBytes(StandardCharsets.UTF_8));
        Instantanea actual = cargar(archivo.toUri().toString());
        comprobarTexto(actual, html);
        comprobar(actual.editoresHtml() == 0, "HTML no debe mostrarse en un JEditorPane.");
        comprobar(!actual.editable(), "El HTML literal debe ser de solo lectura.");
        comprobar(Files.readString(archivo).equals(html), "El archivo HTML debe permanecer intacto.");
    }

    private void probarImagen() throws Exception {
        Instantanea actual = cargar(crearImagen().toUri().toString());
        comprobar(actual.estado().startsWith("Imagen: 40 × 20"), "Debe decodificarse la imagen real.");
        comprobar(actual.texto() == null, "La imagen no debe sustituirse por un área de texto.");
        comprobarPintura(pintarImagen(100, 100), new Rectangle(30, 40, 40, 20));
        comprobarPintura(pintarImagen(20, 20), new Rectangle(0, 5, 20, 10));
    }

    private Path crearImagen() throws IOException {
        Path archivo = directorio.resolve("imagen.sin_extension");
        BufferedImage imagen = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D dibujo = imagen.createGraphics();
        try {
            dibujo.setColor(Color.RED);
            dibujo.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
        } finally {
            dibujo.dispose();
        }
        if (!ImageIO.write(imagen, "png", archivo.toFile())) {
            throw new AssertionError("El JDK debe disponer de un escritor PNG.");
        }
        return archivo;
    }

    private BufferedImage pintarImagen(int ancho, int alto) throws Exception {
        return enEdt(() -> {
            Container contenido = (Container) ((BorderLayout) vista.getLayout())
                    .getLayoutComponent(BorderLayout.CENTER);
            Component lienzo = ((BorderLayout) contenido.getLayout())
                    .getLayoutComponent(BorderLayout.CENTER);
            comprobar(lienzo instanceof JPanel, "Se esperaba un panel que dibuje la imagen.");
            lienzo.setBackground(Color.WHITE);
            lienzo.setSize(ancho, alto);
            BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D dibujo = destino.createGraphics();
            try {
                lienzo.paint(dibujo);
            } finally {
                dibujo.dispose();
            }
            return destino;
        });
    }

    private void comprobarPintura(BufferedImage imagen, Rectangle esperado) {
        boolean coincide = true;
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                boolean rojo = imagen.getRGB(x, y) == Color.RED.getRGB();
                coincide &= rojo == esperado.contains(x, y);
            }
        }
        comprobar(coincide, "La imagen debe quedar centrada, proporcional y sin ampliación: " + esperado);
    }

    private void probarFormatosYUri() throws Exception {
        rechazar(escribir("desconocido.dat", "No asumir texto".getBytes(StandardCharsets.UTF_8))
                .toUri().toString(), "Formato no soportado");
        rechazar(escribir("archivo.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8))
                .toUri().toString(), "PDF no tiene vista previa");
        rechazar("https://example.invalid/archivo.txt", "Solo se admiten URI file:");
        rechazar("http://example.invalid/archivo.png", "Solo se admiten URI file:");
        rechazar("file://servidor/archivo.txt", "sin servidor");
        rechazar(directorio.resolve("ausente.txt").toUri().toString(), "no existe");
        rechazar(directorio.toUri().toString(), "carpeta");
        rechazar("file:///ruta con espacios.txt", "no es válida");
        rechazar(directorio.resolve("archivo.txt").toUri() + "#fragmento", "fragmento");
        rechazar(null, "No se ha indicado");
    }

    private void probarErroresDeTexto() throws Exception {
        rechazar(escribir("invalido.txt", new byte[] {(byte) 0xc3, 0x28})
                .toUri().toString(), "UTF-8 válido");
        rechazar(escribir("binario.txt", new byte[] {'A', 0, 'B'})
                .toUri().toString(), "controles binarios");
    }

    private void probarLimitesDeTexto() throws Exception {
        byte[] bytes = new byte[LIMITE_TEXTO];
        Arrays.fill(bytes, (byte) 'x');
        Instantanea actual = cargar(escribir("limite.txt", bytes).toUri().toString());
        comprobarTexto(actual, "x".repeat(LIMITE_TEXTO));
        rechazar(escribir("exceso.txt", Arrays.copyOf(bytes, bytes.length + 1))
                .toUri().toString(), "supera el límite de 1 MiB");
    }

    private void probarLimitesDeImagen() throws Exception {
        Path enorme = directorio.resolve("enorme.png");
        try (RandomAccessFile archivo = new RandomAccessFile(enorme.toFile(), "rw")) {
            archivo.setLength(20L * 1024 * 1024 + 1);
        }
        rechazar(enorme.toUri().toString(), "supera el límite de imágenes de 20 MiB");
        rechazar(imagenConDimensiones("ancha.png", 8193, 1).toUri().toString(), "máximo 8192");
        rechazar(imagenConDimensiones("muchos-pixeles.png", 4001, 4000).toUri().toString(),
                "16 000 000 píxeles");
    }

    private Path imagenConDimensiones(String nombre, int ancho, int alto) throws IOException {
        // IHDR y CRC válidos, pero sin datos para esas dimensiones: debe
        // rechazarse por la cabecera, antes de intentar decodificar los píxeles.
        byte[] png = Files.readAllBytes(crearImagen());
        ByteBuffer cabecera = ByteBuffer.wrap(png);
        cabecera.putInt(16, ancho);
        cabecera.putInt(20, alto);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        cabecera.putInt(29, (int) crc.getValue());
        return escribir(nombre, png);
    }

    private void probarSolicitudesRapidas() throws Exception {
        String anterior = escribir("anterior.txt", "ANTERIOR".getBytes(StandardCharsets.UTF_8))
                .toUri().toString();
        String ultima = escribir("ultima.txt", "ÚLTIMA SOLICITUD".getBytes(StandardCharsets.UTF_8))
                .toUri().toString();
        enEdt(() -> {
            for (int i = 0; i < 50; i++) {
                vista.mostrar(i % 2 == 0 ? anterior : "https://example.invalid/obsoleto");
            }
            vista.mostrar(ultima);
            return null;
        });
        Instantanea actual = esperarCarga();
        comprobarTexto(actual, "ÚLTIMA SOLICITUD");
        comprobarEstable(actual);
    }

    private void probarCicloDeVida() throws Exception {
        String anterior = directorio.resolve("anterior.txt").toUri().toString();
        String ultima = directorio.resolve("ultima.txt").toUri().toString();
        enEdt(() -> {
            vista.mostrar(anterior);
            vista.removeNotify();
            return null;
        });
        Instantanea cerrada = observar();
        comprobar("Sin carga activa.".equals(cerrada.estado()), "removeNotify debe detener la carga.");
        comprobar("Vista previa cerrada.".equals(cerrada.texto()), "El panel debe quedar cerrado.");
        comprobarEstable(cerrada);
        enEdt(() -> {
            vista.mostrar(ultima);
            return null;
        });
        comprobarEstable(cerrada);
        enEdt(() -> {
            vista.addNotify();
            vista.mostrar(ultima);
            return null;
        });
        Instantanea reabierta = esperarCarga();
        comprobarTexto(reabierta, "ÚLTIMA SOLICITUD");
        comprobarEstable(reabierta);
    }

    private void probarExigenciaDeEdt() {
        boolean rechazada = false;
        try {
            vista.mostrar(directorio.resolve("ultima.txt").toUri().toString());
        } catch (IllegalStateException e) {
            rechazada = true;
        }
        comprobar(rechazada, "mostrar debe rechazar llamadas fuera del EDT.");
    }

    private Path escribir(String nombre, byte[] bytes) throws IOException {
        return Files.write(directorio.resolve(nombre), bytes);
    }

    private Instantanea cargar(String uri) throws Exception {
        enEdt(() -> {
            vista.mostrar(uri);
            return null;
        });
        return esperarCarga();
    }

    private void rechazar(String uri, String mensaje) throws Exception {
        Instantanea actual = cargar(uri);
        comprobar("Vista previa no disponible.".equals(actual.estado()), "Se esperaba un rechazo: " + mensaje);
        comprobar(actual.texto() != null && actual.texto().contains(mensaje),
                "Mensaje esperado: " + mensaje + "; recibido: " + actual.texto());
    }

    private void comprobarTexto(Instantanea actual, String esperado) {
        comprobar(actual.estado().startsWith("Texto UTF-8"), "Se esperaba una vista de texto UTF-8.");
        comprobar(esperado.equals(actual.texto()), "El contenido de texto no coincide exactamente.");
    }

    private Instantanea esperarCarga() throws Exception {
        long inicio = System.nanoTime();
        Instantanea actual;
        do {
            actual = observar();
            if (actual.estado().startsWith("Texto UTF-8") || actual.estado().startsWith("Imagen:")
                    || actual.estado().equals("Vista previa no disponible.")) {
                return actual;
            }
            Thread.sleep(10);
        } while (System.nanoTime() - inicio < ESPERA_CARGA);
        throw new AssertionError("La carga no terminó en 8 segundos. Estado: " + actual.estado());
    }

    private void comprobarEstable(Instantanea esperada) throws Exception {
        // Deja transcurrir varios ciclos de publicación de SwingWorker para
        // detectar callbacks antiguos, sin dormir ni esperar dentro del EDT.
        long inicio = System.nanoTime();
        do {
            if (!esperada.equals(observar())) {
                throw new AssertionError("Una carga obsoleta alteró el contenido del panel.");
            }
            Thread.sleep(10);
        } while (System.nanoTime() - inicio < TimeUnit.MILLISECONDS.toNanos(250));
        comprobar(true, "La vista debe permanecer estable tras las cargas anteriores.");
    }

    private Instantanea observar() throws Exception {
        return enEdt(() -> {
            List<JLabel> etiquetas = buscar(vista, JLabel.class);
            List<JTextArea> textos = buscar(vista, JTextArea.class);
            if (etiquetas.size() != 1 || textos.size() > 1) {
                throw new AssertionError("Se esperaba un estado y, como máximo, un área de texto.");
            }
            JTextArea texto = textos.isEmpty() ? null : textos.get(0);
            return new Instantanea(etiquetas.get(0).getText(), texto == null ? null : texto.getText(),
                    texto != null && texto.isEditable(), texto == null ? null : texto.getFont().getFamily(),
                    buscar(vista, JEditorPane.class).size());
        });
    }

    private static <T extends Component> List<T> buscar(Component raiz, Class<T> tipo) {
        List<T> encontrados = new ArrayList<>();
        if (tipo.isInstance(raiz)) {
            encontrados.add(tipo.cast(raiz));
        }
        if (raiz instanceof Container contenedor) {
            for (Component hijo : contenedor.getComponents()) {
                encontrados.addAll(buscar(hijo, tipo));
            }
        }
        return encontrados;
    }

    private static <T> T enEdt(Callable<T> accion) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Las esperas de la prueba deben hacerse fuera del EDT.");
        }
        FutureTask<T> tarea = new FutureTask<>(accion);
        SwingUtilities.invokeLater(tarea);
        try {
            return tarea.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            tarea.cancel(false);
            throw new AssertionError("El EDT no respondió en 5 segundos.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            if (e.getCause() instanceof Exception excepcion) {
                throw excepcion;
            }
            throw new AssertionError("Fallo inesperado en el EDT.", e.getCause());
        }
    }

    private void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
        comprobaciones++;
    }

    private static void borrarDirectorio(Path directorio) throws IOException {
        Files.walkFileTree(directorio, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) throws IOException {
                Files.delete(archivo);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path carpeta, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(carpeta);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record Instantanea(String estado, String texto, boolean editable,
            String fuente, int editoresHtml) { }
}
