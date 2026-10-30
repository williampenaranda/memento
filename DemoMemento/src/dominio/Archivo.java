package dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

//originador
public class Archivo {
    //inmutable
    private final int id;

    //editable
    private String hashSHA256;
    private String titulo;
    private String autor;
    private String URIAlmacenamiento;

    //atributo auxiliar para generar IDs únicos
    private static final AtomicInteger siguienteId = new AtomicInteger();

    public Archivo(String URIAlmacenamiento) {
        String identificador = UUID.randomUUID().toString();
        this.id = siguienteId.incrementAndGet();
        this.titulo = "Archivo-" + identificador;
        this.autor = "Autor-" + identificador;
        this.hashSHA256 = generarHashDeEjemplo(identificador);
        this.URIAlmacenamiento = URIAlmacenamiento;
    }

    private static String generarHashDeEjemplo(String identificador) {
        try {
            // Este hash corresponde al identificador de ejemplo, no al contenido del archivo.
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identificador.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder();
            for (byte valor : hash) {
                hexadecimal.append(String.format("%02x", valor & 0xff));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    public Archivo(int id, String titulo, String autor, String hashSHA256, String URIAlmacenamiento) {
        this.id = id;
        this.titulo = titulo;
        this.autor = autor;
        this.hashSHA256 = hashSHA256;
        this.URIAlmacenamiento = URIAlmacenamiento;
    }

    public int getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getAutor() {
        return autor;
    }

    public String getHashSHA256() {
        return hashSHA256;
    }

    public String getURIAlmacenamiento() {
        return URIAlmacenamiento;
    }


    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public void setHashSHA256(String hashSHA256) {
        this.hashSHA256 = hashSHA256;
    }

    public void setURIAlmacenamiento(String URIAlmacenamiento) {
        this.URIAlmacenamiento = URIAlmacenamiento;
    }

    public ArchivoMemento guardarEstado() {
        return new ArchivoMemento(titulo, autor, URIAlmacenamiento, hashSHA256);
    }
    public void restaurarEstado(ArchivoMemento memento) {
        this.titulo = memento.getTitulo();
        this.autor = memento.getAutor();
        this.URIAlmacenamiento = memento.getURIAlmacenamiento();
        this.hashSHA256 = memento.getHashSHA256();
    }
}
