# Demostración gráfica de Memento

Interfaz Swing sin dependencias externas. Requiere **JDK 17 o posterior** y un entorno gráfico.

## Compilar y ejecutar

Desde la raíz de este proyecto (`presentacion/`):

```sh
javac -encoding UTF-8 -d bin DemoMemento.java dominio/*.java aplicacion/*.java adaptadores/grafica/*.java pruebas/*.java
java -cp bin presentacion.DemoMemento
```

La ventana abre inicialmente `recursos/documento-demo.txt`. También puedes indicar una ruta local (no una URI) como argumento:

```sh
java -cp bin presentacion.DemoMemento /ruta/al/documento.txt
```
## Recorrido para la presentación

1. Observa el contenido real del documento a la izquierda y sus metadatos a la derecha.
2. Modifica título y autor. Todavía son un borrador: `Archivo` no cambia.
3. Pulsa **Aplicar cambios**. El caretaker guarda un memento del estado anterior y después se modifican los campos del originador.
4. Realiza otra edición y aplícala: ahora hay dos respaldos.
5. Pulsa **Deshacer** dos veces para recuperar los estados anteriores. El ID permanece igual.
6. Con **Elegir…**, selecciona otro archivo y aplica el cambio de URI. Deshacer recupera tanto la URI anterior como su vista previa.

Cada aplicación agrupa los cuatro campos en una sola edición. No se crean respaldos si los valores no han cambiado. Si hay un borrador sin aplicar, **Deshacer solo descarta ese borrador**, sin consumir el historial; otra pulsación restaura el último memento. Sin borrador ni respaldos, el botón queda deshabilitado.

Los metadatos y el historial viven **solo en memoria**. Se pierden al cerrar la ventana. La demo no modifica ni guarda el contenido de los archivos físicos, y no implementa rehacer.

## Participantes y separación

| Clase | Papel |
| --- | --- |
| `dominio/Archivo.java` | Originador: contiene su estado, crea las instantáneas y se restaura. |
| `dominio/ArchivoMemento.java` | Memento inmutable; sus datos no son accesibles desde el adaptador gráfico. |
| `aplicacion/EditorArchivo.java` | Caretaker: conserva una pila de mementos y devuelve el último al originador. |
| `adaptadores/grafica/VentanaArchivo.java` | Adaptador gráfico: presenta campos, solicita guardar antes de aplicar cambios y solicita deshacer. No lee ni almacena mementos. |
| `adaptadores/grafica/VistaPreviaArchivo.java` | Presentación de archivos locales, independiente de la lógica de Memento. |
| `DemoMemento.java` | Entrada que crea y conecta las instancias. |

Se mantienen las API actuales de dominio y aplicación para no ampliar el ejemplo con interfaces o infraestructura adicionales. Esta demo muestra su separación mediante un adaptador Swing; no pretende añadir una arquitectura hexagonal completa.

## Campos y vista previa

- **ID:** solo lectura, inmutable.
- **Título, autor, URI y SHA-256:** editables, siguiendo el modelo existente. Los cuatro se capturan y restauran juntos.
- **SHA-256:** es un dato de ejemplo generado por el constructor, no el hash del contenido real. Es editable y **no se recalcula ni verifica** al cambiar la URI.
- **URI:** admite edición libre como metadato; la vista previa solo acepta URI `file:` locales absolutas. Elegir un archivo produce la URI apropiada, incluidos los espacios codificados. Una URI no válida muestra un error en el panel, sin borrar el historial.
- El contenido mostrado es de **solo lectura**. La vista previa y la apertura externa usan la URI ya aplicada, no el borrador.

### Formatos y límites

- Texto UTF-8: `.txt`, `.md`, `.markdown`, `.java`, `.csv`, `.tsv`, `.json`, `.xml`, `.log`, `.html`, `.htm`, `.css`, `.js`, `.mjs`, `.cjs`, `.ts`, `.tsx`, `.jsx`, `.yaml`, `.yml`, `.properties`, `.ini`, `.cfg`, `.conf`, `.sql`, `.sh`, `.py`. Máximo **1 MiB**; si se supera, se rechaza en lugar de truncarlo. El marcado HTML se muestra como texto, nunca se interpreta.
- Imágenes reconocidas por ImageIO del JDK, como PNG, JPEG y GIF: máximo **20 MiB**, **8192 píxeles por lado** y **16 millones de píxeles** en total. Escalado proporcional sin ampliar el original; solo el primer fotograma, sin animación.
- PDF y otros formatos: sin renderizado integrado. **Abrir externamente** utiliza la aplicación del sistema, si existe una asociación y el escritorio lo permite. Esa aplicación es independiente de la demo y puede permitir modificar el archivo real.
- No se descargan direcciones HTTP/HTTPS. Una ruta local puede apuntar a un montaje de red gestionado por el sistema operativo.

La lectura ocurre en segundo plano para no bloquear la ventana. Si cambia la URI o se deshace durante una carga, los resultados anteriores no sustituyen la vista previa actual. Deshacer restaura metadatos, **no versiones del contenido físico**: si otro programa modifica o elimina el archivo, se verá el contenido actual o un error.

No se modificaron `ArchivoMemento.java`, `Ejemplo.java`, los constructores ni los métodos de creación/restauración del estado.

## Pruebas

Después de compilar:

```sh
java -cp bin presentacion.pruebas.PruebaMemento
java -Djava.awt.headless=true -cp bin presentacion.pruebas.PruebaVistaPrevia
java -cp bin presentacion.pruebas.PruebaInterfaz
```

Las pruebas no necesitan bibliotecas externas. Comprueban restauración de todos los campos, independencia de las instantáneas, estabilidad del ID, orden del historial, historial vacío, vistas previas y cargas obsoletas. La prueba de interfaz comprueba aplicar, descartar borradores y deshacer mediante los controles reales; si no hay entorno gráfico, informa que se omite.
