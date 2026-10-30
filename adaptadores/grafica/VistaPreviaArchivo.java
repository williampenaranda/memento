package presentacion.adaptadores.grafica;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Vista de solo lectura de archivos normales mediante URI file: locales, sin
 * autoridad, consulta ni fragmento. No utiliza clientes de red ni visores HTML.
 * Las rutas pueden resolver enlaces o montajes del sistema de archivos; esta
 * clase no puede determinar si un montaje está respaldado por una red.
 *
 * <p>Texto: UTF-8 estricto, BOM inicial opcional, máximo 1 MiB (1 048 576 bytes).
 * Se rechaza el archivo completo si excede el límite, si el UTF-8 es inválido o
 * si contiene controles ISO distintos de tabulación, LF y CR. Extensiones
 * admitidas, sin distinguir mayúsculas: txt, md, markdown, java, csv, tsv, json,
 * xml, log, html, htm, css, js, mjs, cjs, ts, tsx, jsx, yaml, yml, properties,
 * ini, cfg, conf, sql, sh, py. No se interpreta marcado ni se modifica el archivo.
 *
 * <p>Imágenes: formatos reconocidos por los lectores ImageIO del JDK instalado,
 * máximo 20 MiB (20 971 520 bytes), 8192 píxeles por lado y 16 000 000 píxeles.
 * Se consultan las dimensiones con ImageReader antes de decodificar. Se muestra
 * solo la primera imagen o fotograma, sin animación ni ampliación sobre el 100 %.
 * Otros formatos, incluido PDF, no tienen vista previa. Los límites de bytes
 * también se aplican durante la lectura, aunque el archivo crezca.
 *
 * <p>Construir y llamar a mostrar en el EDT. La lectura se realiza en segundo
 * plano; una nueva solicitud o removeNotify cancela e invalida la carga anterior.
 * Tras volver a añadir el panel, llamar a mostrar para iniciar una nueva carga.
 */
public final class VistaPreviaArchivo extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int LIMITE_TEXTO = 1024 * 1024;
    private static final int LIMITE_IMAGEN = 20 * 1024 * 1024;
    private static final int LIMITE_LADO = 8192;
    private static final long LIMITE_PIXELES = 16_000_000L;
    private static final Set<String> EXTENSIONES_TEXTO = Set.of(
            "txt", "md", "markdown", "java", "csv", "tsv", "json", "xml", "log",
            "html", "htm", "css", "js", "mjs", "cjs", "ts", "tsx", "jsx", "yaml",
            "yml", "properties", "ini", "cfg", "conf", "sql", "sh", "py");

    private final JPanel contenido = new JPanel(new BorderLayout());
    private final JLabel estado = new JLabel();
    private SwingWorker<Resultado, Void> trabajador;
    private long solicitud;
    private boolean retirado;

    public VistaPreviaArchivo() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        estado.putClientProperty("html.disable", Boolean.TRUE);
        add(contenido, BorderLayout.CENTER);
        add(estado, BorderLayout.SOUTH);
        mostrarMensaje("Seleccione un archivo para ver su contenido.");
        estado.setText("Sin archivo seleccionado.");
    }

    /**
     * Inicia una vista previa asíncrona. Las URI inválidas se notifican en el
     * panel. Si el panel ha sido retirado, se ignora la solicitud hasta addNotify.
     *
     * @param uri URI file: absoluta del archivo, con los espacios codificados
     * @throws IllegalStateException si no se llama desde el EDT
     */
    public void mostrar(String uri) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("mostrar debe llamarse desde el EDT.");
        }
        cancelarCarga();
        if (retirado) {
            return;
        }
        long actual = solicitud;
        mostrarMensaje("Cargando vista previa…");
        estado.setText("Leyendo archivo local…");
        trabajador = new SwingWorker<Resultado, Void>() {
            @Override
            protected Resultado doInBackground() throws IOException {
                return cargar(uri);
            }

            @Override
            protected void done() {
                if (retirado || actual != solicitud || trabajador != this) {
                    return;
                }
                trabajador = null;
                try {
                    presentar(get());
                } catch (CancellationException e) {
                    mostrarError("Carga cancelada.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    mostrarError("Carga interrumpida.");
                } catch (ExecutionException e) {
                    mostrarError(describirError(e.getCause()));
                }
            }
        };
        trabajador.execute();
    }

    @Override
    public void removeNotify() {
        retirado = true;
        cancelarCarga();
        mostrarMensaje("Vista previa cerrada.");
        estado.setText("Sin carga activa.");
        super.removeNotify();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        retirado = false;
    }

    private void cancelarCarga() {
        solicitud++;
        if (trabajador != null) {
            trabajador.cancel(true);
            trabajador = null;
        }
    }

    private static Resultado cargar(String textoUri) throws IOException {
        comprobarCancelacion();
        Path archivo = rutaLocal(textoUri);
        BasicFileAttributes atributos = Files.readAttributes(archivo, BasicFileAttributes.class);
        if (atributos.isDirectory()) {
            throw new ErrorVista("La ruta es una carpeta; seleccione un archivo normal.");
        }
        if (!atributos.isRegularFile()) {
            throw new ErrorVista("La ruta no corresponde a un archivo normal.");
        }
        String nombre = archivo.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        String extension = punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (EXTENSIONES_TEXTO.contains(extension)) {
            return leerTexto(archivo, atributos.size());
        }
        if (extension.equals("pdf")) {
            throw new ErrorVista("PDF no tiene vista previa; utilice la apertura externa.");
        }
        return leerImagen(archivo, atributos.size());
    }

    private static Path rutaLocal(String textoUri) throws ErrorVista {
        if (textoUri == null || textoUri.isBlank()) {
            throw new ErrorVista("No se ha indicado una URI de archivo.");
        }
        try {
            URI uri = new URI(textoUri);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                throw new ErrorVista("Solo se admiten URI file: locales; no se accede a direcciones de red.");
            }
            if (uri.getRawAuthority() != null || uri.getPath() == null
                    || uri.getPath().startsWith("//")) {
                throw new ErrorVista("La URI file: debe ser local, sin servidor ni ruta de red.");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new ErrorVista("La URI file: no debe contener consulta ni fragmento.");
            }
            return Path.of(uri);
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new ErrorVista("La URI file: no es válida; use una URI absoluta con espacios codificados.");
        }
    }

    private static Resultado leerTexto(Path archivo, long tamano) throws IOException {
        String avisoLimite = "El texto supera el límite de 1 MiB (1 048 576 bytes); no se muestra parcialmente.";
        if (tamano > LIMITE_TEXTO) {
            throw new ErrorVista(avisoLimite);
        }
        byte[] bytes;
        try (InputStream entrada = new EntradaLimitada(
                Files.newInputStream(archivo), LIMITE_TEXTO, avisoLimite)) {
            bytes = entrada.readAllBytes();
        }
        comprobarCancelacion();
        String texto = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (texto.startsWith("\uFEFF")) {
            texto = texto.substring(1);
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                throw new ErrorVista("El archivo contiene controles binarios; no se muestra como texto.");
            }
        }
        comprobarCancelacion();
        return new Resultado(texto, null);
    }

    private static Resultado leerImagen(Path archivo, long tamano) throws IOException {
        String avisoLimite = "El archivo supera el límite de imágenes de 20 MiB (20 971 520 bytes).";
        if (tamano > LIMITE_IMAGEN) {
            throw new ErrorVista(avisoLimite);
        }
        try (InputStream entrada = new EntradaLimitada(
                    Files.newInputStream(archivo), LIMITE_IMAGEN, avisoLimite);
                ImageInputStream imagenEntrada = new MemoryCacheImageInputStream(entrada)) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(imagenEntrada);
            if (!lectores.hasNext()) {
                throw new ErrorVista("Formato no soportado: no es una imagen ImageIO ni tiene una extensión de texto admitida. Utilice la apertura externa.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(imagenEntrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                comprobarDimensiones(ancho, alto);
                comprobarCancelacion();
                BufferedImage imagen = lector.read(0);
                comprobarCancelacion();
                if (imagen == null) {
                    throw new ErrorVista("La imagen no se puede decodificar.");
                }
                comprobarDimensiones(imagen.getWidth(), imagen.getHeight());
                return new Resultado(null, imagen);
            } finally {
                lector.dispose();
            }
        }
    }

    private static void comprobarDimensiones(int ancho, int alto) throws ErrorVista {
        if (ancho <= 0 || alto <= 0 || ancho > LIMITE_LADO || alto > LIMITE_LADO
                || (long) ancho * alto > LIMITE_PIXELES) {
            throw new ErrorVista("Imagen rechazada: máximo 8192 píxeles por lado y 16 000 000 píxeles en total.");
        }
    }

    private static void comprobarCancelacion() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Carga cancelada.");
        }
    }

    private static String describirError(Throwable error) {
        if (error instanceof ErrorVista) {
            return error.getMessage();
        }
        if (error instanceof NoSuchFileException) {
            return "El archivo no existe o ha sido eliminado.";
        }
        if (error instanceof AccessDeniedException || error instanceof SecurityException) {
            return "No hay permiso para leer el archivo.";
        }
        if (error instanceof CharacterCodingException) {
            return "El archivo no contiene texto UTF-8 válido; no se muestra con caracteres sustituidos.";
        }
        if (error instanceof InterruptedIOException) {
            return "Carga cancelada.";
        }
        if (error instanceof IOException) {
            return "No se pudo leer el archivo: puede estar dañado, haber cambiado o no ser una imagen válida.";
        }
        return "No se pudo generar la vista previa de este archivo.";
    }

    private void presentar(Resultado resultado) {
        contenido.removeAll();
        if (resultado.imagen() != null) {
            BufferedImage imagen = resultado.imagen();
            contenido.add(new PanelImagen(imagen), BorderLayout.CENTER);
            estado.setText("Imagen: " + imagen.getWidth() + " × " + imagen.getHeight()
                    + " píxeles · primer fotograma · sin ampliación.");
        } else {
            JTextArea texto = new JTextArea(resultado.texto());
            texto.setEditable(false);
            texto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            texto.setTabSize(4);
            texto.setMargin(new Insets(8, 8, 8, 8));
            texto.setLineWrap(false);
            texto.setCaretPosition(0);
            contenido.add(new JScrollPane(texto), BorderLayout.CENTER);
            estado.setText("Texto UTF-8 · solo lectura · máximo 1 MiB.");
        }
        contenido.revalidate();
        contenido.repaint();
    }

    private void mostrarError(String mensaje) {
        mostrarMensaje(mensaje);
        estado.setText("Vista previa no disponible.");
    }

    private void mostrarMensaje(String mensaje) {
        JTextArea texto = new JTextArea(mensaje);
        texto.setEditable(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        texto.setMargin(new Insets(8, 8, 8, 8));
        contenido.removeAll();
        contenido.add(new JScrollPane(texto), BorderLayout.CENTER);
        contenido.revalidate();
        contenido.repaint();
    }

    private record Resultado(String texto, BufferedImage imagen) { }

    private static final class ErrorVista extends IOException {
        private static final long serialVersionUID = 1L;

        ErrorVista(String mensaje) {
            super(mensaje);
        }
    }

    /** Limita también lecturas posteriores a la consulta inicial del tamaño. */
    private static final class EntradaLimitada extends InputStream {
        private final InputStream entrada;
        private final int limite;
        private final String mensaje;
        private int leidos;

        EntradaLimitada(InputStream entrada, int limite, String mensaje) {
            this.entrada = entrada;
            this.limite = limite;
            this.mensaje = mensaje;
        }

        @Override
        public int read() throws IOException {
            comprobarCancelacion();
            int valor = entrada.read();
            if (valor != -1) {
                comprobarLimite(1);
            }
            return valor;
        }

        @Override
        public int read(byte[] bytes, int inicio, int longitud) throws IOException {
            comprobarCancelacion();
            int cantidad = entrada.read(bytes, inicio, Math.min(longitud, limite - leidos + 1));
            if (cantidad > 0) {
                comprobarLimite(cantidad);
            }
            return cantidad;
        }

        private void comprobarLimite(int cantidad) throws ErrorVista {
            leidos += cantidad;
            if (leidos > limite) {
                throw new ErrorVista(mensaje);
            }
        }

        @Override
        public void close() throws IOException {
            entrada.close();
        }
    }

    private static final class PanelImagen extends JPanel {
        private static final long serialVersionUID = 1L;
        private final BufferedImage imagen;

        PanelImagen(BufferedImage imagen) {
            this.imagen = imagen;
            setPreferredSize(new Dimension(Math.min(640, imagen.getWidth()),
                    Math.min(480, imagen.getHeight())));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            double escala = Math.min(1.0, Math.min((double) getWidth() / imagen.getWidth(),
                    (double) getHeight() / imagen.getHeight()));
            if (escala <= 0) {
                return;
            }
            int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
            int alto = Math.max(1, (int) Math.round(imagen.getHeight() * escala));
            Graphics2D dibujo = (Graphics2D) g.create();
            try {
                dibujo.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                dibujo.drawImage(imagen, (getWidth() - ancho) / 2, (getHeight() - alto) / 2,
                        ancho, alto, null);
            } finally {
                dibujo.dispose();
            }
        }
    }
}
