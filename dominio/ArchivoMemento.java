package presentacion.dominio;

public final class ArchivoMemento {
    private final String titulo;
    private final String autor;
    private final String URIAlmacenamiento;
    private final String hashSHA256;

    ArchivoMemento(String titulo, String autor, String URIAlmacenamiento, String hashSHA256) {
        this.titulo = titulo;
        this.autor = autor;
        this.URIAlmacenamiento = URIAlmacenamiento;
        this.hashSHA256 = hashSHA256;
    }

    String getTitulo() {
        return titulo;
    }

    String getAutor() {
        return autor;
    }

    String getURIAlmacenamiento() {
        return URIAlmacenamiento;
    }

    String getHashSHA256() {
        return hashSHA256;
    }
}
