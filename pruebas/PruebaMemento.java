package presentacion.pruebas;

import presentacion.aplicacion.EditorArchivo;
import presentacion.dominio.Archivo;
import presentacion.dominio.ArchivoMemento;

import java.util.Objects;

public final class PruebaMemento {
    private PruebaMemento() {
    }

    public static void main(String[] args) {
        probarInstantaneaIndependiente();
        probarHistorial();
        System.out.println("Pruebas de Memento correctas: instantánea, cuatro campos, ID y pila de deshacer.");
    }

    private static void probarInstantaneaIndependiente() {
        Archivo archivo = new Archivo(42, "Original", "Ana", "hash-original", "file:///original.txt");
        ArchivoMemento respaldo = archivo.guardarEstado();
        editar(archivo, "Editado", "Luis", "hash-editado", "file:///editado.txt");
        archivo.restaurarEstado(respaldo);
        comprobarEstado(archivo, 42, "Original", "Ana", "hash-original", "file:///original.txt");

        editar(archivo, "Otro", "Eva", "otro-hash", "file:///otro.txt");
        archivo.restaurarEstado(respaldo);
        comprobarEstado(archivo, 42, "Original", "Ana", "hash-original", "file:///original.txt");
    }

    private static void probarHistorial() {
        Archivo archivo = new Archivo("file:///inicial.txt");
        int id = archivo.getId();
        String titulo = archivo.getTitulo();
        String autor = archivo.getAutor();
        String hash = archivo.getHashSHA256();
        EditorArchivo editor = new EditorArchivo(archivo);
        comprobar(!editor.deshacer(), "Un historial vacío debe devolver false.");

        editor.guardar();
        editar(archivo, "Versión uno", "Ana", "hash-uno", "file:///uno.txt");
        editor.guardar();
        editar(archivo, "Versión dos", "Luis", "hash-dos", "file:///dos.txt");

        comprobar(editor.deshacer(), "Debe poder deshacer la segunda edición.");
        comprobarEstado(archivo, id, "Versión uno", "Ana", "hash-uno", "file:///uno.txt");
        comprobar(editor.deshacer(), "Debe poder deshacer la primera edición.");
        comprobarEstado(archivo, id, titulo, autor, hash, "file:///inicial.txt");
        comprobar(!editor.deshacer(), "No deben quedar respaldos.");
        comprobarEstado(archivo, id, titulo, autor, hash, "file:///inicial.txt");

        editor.guardar();
        editar(archivo, "Nueva edición", "Eva", "hash-nuevo", "file:///nuevo.txt");
        comprobar(editor.deshacer(), "Debe poder editar después de agotar el historial.");
        comprobarEstado(archivo, id, titulo, autor, hash, "file:///inicial.txt");
    }

    private static void editar(Archivo archivo, String titulo, String autor, String hash, String uri) {
        archivo.setTitulo(titulo);
        archivo.setAutor(autor);
        archivo.setHashSHA256(hash);
        archivo.setURIAlmacenamiento(uri);
    }

    private static void comprobarEstado(Archivo archivo, int id, String titulo,
            String autor, String hash, String uri) {
        comprobar(archivo.getId() == id, "El ID debe permanecer intacto.");
        comprobar(Objects.equals(archivo.getTitulo(), titulo), "Título incorrecto.");
        comprobar(Objects.equals(archivo.getAutor(), autor), "Autor incorrecto.");
        comprobar(Objects.equals(archivo.getHashSHA256(), hash), "Hash incorrecto.");
        comprobar(Objects.equals(archivo.getURIAlmacenamiento(), uri), "URI incorrecta.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
