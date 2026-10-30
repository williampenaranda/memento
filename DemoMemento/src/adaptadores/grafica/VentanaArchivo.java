package adaptadores.grafica;

import aplicacion.EditorArchivo;
import dominio.Archivo;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class VentanaArchivo extends JFrame {
    private static final long serialVersionUID = 1L;

    private final Archivo archivo;
    private final EditorArchivo editor;
    private final VistaPreviaArchivo vistaPrevia = new VistaPreviaArchivo();
    private final JTextField id = new JTextField();
    private final JTextField titulo = new JTextField(28);
    private final JTextField autor = new JTextField(28);
    private final JTextField uri = new JTextField(28);
    private final JTextField hash = new JTextField(28);
    private final JButton aplicar = new JButton("Aplicar cambios");
    private final JButton deshacer = new JButton("Deshacer");
    private final JButton abrirExterno = new JButton("Abrir externamente");
    private final JLabel estado = new JLabel("Edita los campos y aplica los cambios para guardar un respaldo.");
    private final JLabel pasos = new JLabel();
    private int respaldos;
    private boolean actualizando;

    public VentanaArchivo(Archivo archivo, EditorArchivo editor) {
        super("Memento — editor de metadatos");
        this.archivo = Objects.requireNonNull(archivo);
        this.editor = Objects.requireNonNull(editor);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(940, 620));
        setSize(1160, 720);
        setLocationRelativeTo(null);

        JPanel documento = new JPanel(new BorderLayout(8, 8));
        documento.setBorder(BorderFactory.createTitledBorder("Contenido del archivo · solo lectura"));
        documento.add(vistaPrevia, BorderLayout.CENTER);
        JPanel accionesVista = new JPanel(new FlowLayout(FlowLayout.LEFT));
        accionesVista.add(abrirExterno);
        documento.add(accionesVista, BorderLayout.SOUTH);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, documento, crearFormulario());
        division.setResizeWeight(0.6);
        division.setDividerLocation(650);
        JPanel principal = new JPanel(new BorderLayout(12, 12));
        principal.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        principal.add(new JLabel("Demo en memoria: guardar estado → editar → restaurar estado"), BorderLayout.NORTH);
        principal.add(division, BorderLayout.CENTER);
        principal.add(estado, BorderLayout.SOUTH);
        setContentPane(principal);

        aplicar.addActionListener(evento -> aplicarCambios());
        deshacer.addActionListener(evento -> deshacerCambio());
        abrirExterno.addActionListener(evento -> abrirExternamente());
        abrirExterno.setEnabled(Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN));
        refrescarCampos();
        escucharEdicion();
        vistaPrevia.mostrar(archivo.getURIAlmacenamiento());
    }

    private JPanel crearFormulario() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createTitledBorder("Estado del originador: Archivo"));
        id.setEditable(false);
        agregarCampo(formulario, 0, "ID (inmutable)", id);
        agregarCampo(formulario, 1, "Título", titulo);
        agregarCampo(formulario, 2, "Autor", autor);

        JButton elegir = new JButton("Elegir…");
        elegir.addActionListener(evento -> elegirArchivo());
        JPanel ubicacion = new JPanel(new BorderLayout(4, 0));
        ubicacion.add(uri, BorderLayout.CENTER);
        ubicacion.add(elegir, BorderLayout.EAST);
        agregarCampo(formulario, 3, "URI de almacenamiento", ubicacion);
        agregarCampo(formulario, 4, "SHA-256 (dato de ejemplo)", hash);
        hash.setToolTipText("Es editable y se restaura con el memento; no se recalcula desde el archivo.");

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        botones.add(aplicar);
        botones.add(deshacer);
        GridBagConstraints posicion = new GridBagConstraints();
        posicion.gridx = 0;
        posicion.gridy = 10;
        posicion.weightx = 1;
        posicion.fill = GridBagConstraints.HORIZONTAL;
        posicion.insets = new Insets(8, 12, 0, 12);
        formulario.add(botones, posicion);
        posicion.gridy++;
        formulario.add(pasos, posicion);
        posicion.gridy++;
        formulario.add(new JLabel("Aplicar guarda un respaldo de los cuatro campos."), posicion);
        posicion.gridy++;
        formulario.add(new JLabel("El contenido del archivo físico no se modifica."), posicion);
        posicion.gridy++;
        posicion.weighty = 1;
        posicion.fill = GridBagConstraints.BOTH;
        formulario.add(new JPanel(), posicion);
        formulario.setMinimumSize(new Dimension(400, 0));
        return formulario;
    }

    private static void agregarCampo(JPanel panel, int fila, String nombre, java.awt.Component campo) {
        GridBagConstraints posicion = new GridBagConstraints();
        posicion.gridx = 0;
        posicion.gridy = fila * 2;
        posicion.weightx = 1;
        posicion.fill = GridBagConstraints.HORIZONTAL;
        posicion.insets = new Insets(10, 12, 4, 12);
        JLabel etiqueta = new JLabel(nombre);
        etiqueta.setLabelFor(campo);
        panel.add(etiqueta, posicion);
        posicion.gridy++;
        posicion.insets = new Insets(0, 12, 0, 12);
        panel.add(campo, posicion);
    }

    private void escucharEdicion() {
        DocumentListener escucha = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent evento) { cambio(); }
            @Override public void removeUpdate(DocumentEvent evento) { cambio(); }
            @Override public void changedUpdate(DocumentEvent evento) { cambio(); }

            private void cambio() {
                if (!actualizando) {
                    actualizarBotones();
                    estado.setText(hayBorrador()
                            ? "Cambios sin aplicar. Deshacer los descarta sin consumir un respaldo."
                            : "Los campos coinciden con el estado actual del archivo.");
                }
            }
        };
        for (JTextField campo : new JTextField[] {titulo, autor, uri, hash}) {
            campo.getDocument().addDocumentListener(escucha);
        }
    }

    private boolean hayBorrador() {
        return !Objects.equals(titulo.getText(), archivo.getTitulo())
                || !Objects.equals(autor.getText(), archivo.getAutor())
                || !Objects.equals(uri.getText(), archivo.getURIAlmacenamiento())
                || !Objects.equals(hash.getText(), archivo.getHashSHA256());
    }

    private void aplicarCambios() {
        if (!hayBorrador()) {
            return;
        }
        // El caretaker conserva la instantánea antes de modificar el originador.
        editor.guardar();
        archivo.setTitulo(titulo.getText());
        archivo.setAutor(autor.getText());
        archivo.setURIAlmacenamiento(uri.getText());
        archivo.setHashSHA256(hash.getText());
        respaldos++;
        actualizarBotones();
        vistaPrevia.mostrar(archivo.getURIAlmacenamiento());
        estado.setText("Memento guardado por el caretaker. Cambios aplicados al originador.");
    }

    private void deshacerCambio() {
        if (hayBorrador()) {
            refrescarCampos();
            estado.setText("Borrador descartado. El historial de mementos no ha cambiado.");
        } else if (editor.deshacer()) {
            respaldos--;
            refrescarCampos();
            vistaPrevia.mostrar(archivo.getURIAlmacenamiento());
            estado.setText("El caretaker devolvió el memento y Archivo restauró su estado anterior.");
        }
    }

    private void refrescarCampos() {
        actualizando = true;
        try {
            id.setText(Integer.toString(archivo.getId()));
            titulo.setText(archivo.getTitulo());
            autor.setText(archivo.getAutor());
            uri.setText(archivo.getURIAlmacenamiento());
            hash.setText(archivo.getHashSHA256());
            for (JTextField campo : new JTextField[] {titulo, autor, uri, hash}) {
                campo.setCaretPosition(0);
            }
        } finally {
            actualizando = false;
        }
        actualizarBotones();
    }

    private void actualizarBotones() {
        aplicar.setEnabled(hayBorrador());
        deshacer.setEnabled(hayBorrador() || respaldos > 0);
        pasos.setText("Respaldos disponibles: " + respaldos);
    }

    private void elegirArchivo() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccionar archivo local para la vista previa");
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            uri.setText(selector.getSelectedFile().toPath().toAbsolutePath().toUri().toString());
            estado.setText("URI seleccionada. Pulsa Aplicar cambios para cargar el archivo y guardar un respaldo.");
        }
    }

    private void abrirExternamente() {
        String uriActual = archivo.getURIAlmacenamiento();
        abrirExterno.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                URI ubicacion = URI.create(uriActual);
                if (!"file".equalsIgnoreCase(ubicacion.getScheme()) || ubicacion.getAuthority() != null) {
                    throw new IllegalArgumentException("Solo se pueden abrir archivos locales con URI file:.");
                }
                Path ruta = Path.of(ubicacion);
                if (!Files.isRegularFile(ruta)) {
                    throw new IllegalArgumentException("La URI no corresponde a un archivo local existente.");
                }
                Desktop.getDesktop().open(ruta.toFile());
                return null;
            }

            @Override
            protected void done() {
                abrirExterno.setEnabled(true);
                try {
                    get();
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(VentanaArchivo.this,
                            "No se pudo abrir el archivo con la aplicación del sistema.\n"
                                    + "Comprueba su URI y que exista una aplicación asociada.",
                            "Abrir archivo", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }
}
